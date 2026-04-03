package com.loopers.application.queue;

import com.loopers.application.queue.dto.QueueEntryResponse;
import com.loopers.application.queue.dto.QueuePositionResponse;
import com.loopers.domain.queue.RedisLockRepository;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import com.loopers.interfaces.scheduler.PositionCacheScheduler;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueFacade {

    private static final String LOCK_ADMISSION = "lock:admission";
    private static final String LOCK_SESSION_GC = "lock:session-gc";

    private final QueueService queueService;
    private final SessionService sessionService;
    private final ModeManager modeManager;
    private final RedisLockRepository lockRepository;
    private final PositionCacheScheduler positionCacheScheduler;
    private final QueueProperties props;
    private final MeterRegistry meterRegistry;

    // Command

    public QueueEntryResponse enter(Long userId) {
        if (!modeManager.isEvent()) {
            if (modeManager.isDrain()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "대기열이 마감되었습니다");
            }
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트가 진행 중이 아닙니다");
        }

        // 재진입 정책: 세션 확인
        SessionService.SessionInfo session = sessionService.getSession(userId);
        if (session != null) {
            if (SessionService.STATUS_ACTIVE.equals(session.status())) {
                throw new CoreException(ErrorType.CONFLICT, "이미 입장하셨습니다");
            }
            if (SessionService.STATUS_CONSUMED.equals(session.status())) {
                sessionService.deleteSession(userId);
            }
        }

        // 원자적 ZADD + 크기 상한 체크
        boolean added = queueService.addToQueue(userId);
        if (!added) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요");
        }

        // 스케줄러 경합 방어: ZADD 직후 세션이 생겨있으면 대기열에서 제거
        if (sessionService.hasSession(userId)) {
            queueService.removeFromQueue(userId.toString());
            throw new CoreException(ErrorType.CONFLICT, "이미 입장하셨습니다");
        }

        Long position = queueService.getPosition(userId);
        long pos = (position != null) ? position : 1;
        long estimatedWait = queueService.estimateWaitSeconds(pos);
        return QueueEntryResponse.waiting(pos, estimatedWait);
    }

    public void changeMode(String mode) {
        switch (mode) {
            case "EVENT" -> modeManager.switchToEvent();
            case "DRAIN" -> modeManager.switchToDrain();
            case "NORMAL" -> modeManager.switchToNormal();
        }
    }

    /**
     * 입장 스케줄러 로직. AdmissionScheduler에서 위임받아 실행.
     * 분산 락 → ZRANGE → 세션 발급(성공분만) → ZREM
     */
    public void admitBatch() {
        if (!modeManager.isEvent()) return;
        if (!lockRepository.tryLock(LOCK_ADMISSION, 15)) return;

        try {
            int batchSize = props.getAdmissionBatchSize();
            Set<String> members = queueService.peekTop(batchSize);
            if (members == null || members.isEmpty()) return;

            List<String> admitted = new ArrayList<>();
            for (String userId : members) {
                try {
                    sessionService.createSession(Long.parseLong(userId));
                    admitted.add(userId);
                } catch (Exception e) {
                    log.warn("세션 발급 실패: userId={}", userId, e);
                }
            }

            if (!admitted.isEmpty()) {
                queueService.removeFromQueue(admitted.toArray(new String[0]));
                meterRegistry.counter("admission.batch.total").increment(admitted.size());
            }
        } catch (Exception e) {
            log.error("입장 배치 처리 오류", e);
        }
    }

    /**
     * GC 스케줄러 로직. SessionGCScheduler에서 위임받아 실행.
     * 분산 락 → ZREMRANGEBYSCORE로 만료 tracker 일괄 정리
     */
    public void cleanExpiredSessions() {
        if (!modeManager.isEvent() && !modeManager.isDrain()) return;
        if (!lockRepository.tryLock(LOCK_SESSION_GC, 15)) return;

        try {
            double now = Instant.now().getEpochSecond();
            sessionService.removeExpiredTrackerEntries(now);
        } catch (Exception e) {
            log.error("SessionGC 오류", e);
        }
    }

    // Query

    public QueuePositionResponse getPosition(Long userId) {
        if (sessionService.hasActiveSession(userId)) {
            return QueuePositionResponse.ready();
        }

        Long cachedPosition = positionCacheScheduler.getCachedPosition(userId);
        if (cachedPosition != null) {
            return QueuePositionResponse.waiting(cachedPosition, queueService.estimateWaitSeconds(cachedPosition));
        }

        Long position = queueService.getPosition(userId);
        if (position == null) {
            return QueuePositionResponse.notInQueue();
        }

        return QueuePositionResponse.waiting(position, queueService.estimateWaitSeconds(position));
    }
}
