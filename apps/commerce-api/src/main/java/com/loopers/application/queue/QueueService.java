package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 대기열 CRUD ApplicationService.
 * QueueRepository(Redis) 조작만 담당.
 * 비즈니스 판단(모드 체크, 세션 확인, 재진입 정책)은 QueueFacade 책임.
 */
@Service
public class QueueService {

    private final QueueRepository queueRepository;
    private final QueueProperties props;

    // 입장 속도 추정
    private volatile double currentAdmissionRate = 125.0;

    public QueueService(QueueRepository queueRepository, QueueProperties props) {
        this.queueRepository = queueRepository;
        this.props = props;
    }

    // Command

    public boolean addToQueue(Long userId) {
        return queueRepository.enqueue(userId, props.getMaxQueueSize());
    }

    public void removeFromQueue(String... userIds) {
        queueRepository.dequeue(userIds);
    }

    public Set<String> peekTop(int count) {
        return queueRepository.peekTop(count);
    }

    public void updateAdmissionRate(double rate) {
        if (rate > 0) {
            this.currentAdmissionRate = rate;
        }
    }

    // Query

    public Long getPosition(Long userId) {
        return queueRepository.getRank(userId);
    }

    public long getQueueSize() {
        return queueRepository.size();
    }

    public long estimateWaitSeconds(long position) {
        double rate = currentAdmissionRate;
        if (rate <= 0) {
            return Math.max(1, position / 125);
        }
        return Math.max(1, (long) (position / rate));
    }
}
