package com.loopers.application.queue;

import com.loopers.domain.queue.SessionRepository;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SessionService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CONSUMED = "CONSUMED";

    /** CAS 결과 */
    public static final long CAS_SUCCESS = 1;
    public static final long CAS_STATUS_MISMATCH = 0;
    public static final long CAS_KEY_NOT_FOUND = -1;

    /** 접근 요청 유형 — 비즈니스 컨텍스트 */
    public enum AccessType { ORDER, QUERY }

    private final SessionRepository sessionRepository;
    private final ModeManager modeManager;
    private final QueueProperties props;

    public SessionService(
            SessionRepository sessionRepository,
            ModeManager modeManager,
            QueueProperties props
    ) {
        this.sessionRepository = sessionRepository;
        this.modeManager = modeManager;
        this.props = props;
    }

    // Command

    public void createSession(Long userId) {
        sessionRepository.create(userId, props.getAccessTtlSeconds(), props.getHardTtlSeconds());
    }

    public long compareAndSwap(Long userId, String expectedStatus, String newStatus) {
        return sessionRepository.compareAndSwap(userId, expectedStatus, newStatus);
    }

    public void deleteSession(Long userId) {
        sessionRepository.delete(userId);
    }

    public void removeExpiredTrackerEntries(double maxScore) {
        sessionRepository.removeExpiredTrackerEntries(maxScore);
    }

    public void clearTracker() {
        sessionRepository.clearTracker();
    }

    // Query

    /**
     * 세션 기반 접근 검증 — 비즈니스 판단 + TTL 연장(부수효과)을 포함.
     * 필터는 이 결과만 받아서 통과/차단.
     */
    public SessionValidation validateAccess(Long userId, AccessType type) {
        SessionInfo session;
        try {
            session = getSession(userId);
        } catch (Exception e) {
            // Redis 장애 + Grace Period → 소프트 바이패스
            if (modeManager.isInGracePeriod()) {
                log.warn("Grace Period 소프트 바이패스: userId={}, Redis 장애", userId, e);
                return SessionValidation.allowed();
            }
            log.error("세션 조회 실패: userId={}", userId, e);
            return SessionValidation.denied(HttpStatus.INTERNAL_SERVER_ERROR, "일시적 오류가 발생했습니다");
        }

        if (session == null) {
            return SessionValidation.denied(HttpStatus.FORBIDDEN, "대기열을 통해 진입해주세요");
        }

        // 주문은 ACTIVE만 허용, 조회는 ACTIVE+CONSUMED 허용
        if (type == AccessType.ORDER && !STATUS_ACTIVE.equals(session.status())) {
            return SessionValidation.denied(HttpStatus.FORBIDDEN, "주문 가능한 세션 상태가 아닙니다");
        }

        // TTL 연장 (부수효과)
        try {
            extendTtl(userId);
        } catch (Exception e) {
            log.warn("세션 TTL 연장 실패: userId={}", userId, e);
        }

        return SessionValidation.allowed();
    }

    public SessionInfo getSession(Long userId) {
        SessionRepository.SessionData data = sessionRepository.find(userId);
        if (data == null) return null;
        return new SessionInfo(data.status(), data.createdAt());
    }

    public boolean hasActiveSession(Long userId) {
        SessionInfo session = getSession(userId);
        return session != null && STATUS_ACTIVE.equals(session.status());
    }

    public boolean hasSession(Long userId) {
        return sessionRepository.exists(userId);
    }

    private boolean extendTtl(Long userId) {
        return sessionRepository.extendTtl(
                userId,
                props.getHardTtlSeconds(),
                props.getActivityExtensionSeconds(),
                props.getMaxExtensionsPerMinute()
        );
    }

    public record SessionInfo(String status, String createdAt) {}

    public record SessionValidation(boolean isAllowed, HttpStatus httpStatus, String message) {
        public static SessionValidation allowed() {
            return new SessionValidation(true, null, null);
        }

        public static SessionValidation denied(HttpStatus status, String message) {
            return new SessionValidation(false, status, message);
        }
    }
}
