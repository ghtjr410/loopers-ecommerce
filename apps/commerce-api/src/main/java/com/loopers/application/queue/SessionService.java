package com.loopers.application.queue;

import com.loopers.domain.queue.SessionRepository;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CONSUMED = "CONSUMED";

    /** CAS 결과 */
    public static final long CAS_SUCCESS = 1;
    public static final long CAS_STATUS_MISMATCH = 0;
    public static final long CAS_KEY_NOT_FOUND = -1;

    private final SessionRepository sessionRepository;
    private final QueueProperties props;

    public SessionService(SessionRepository sessionRepository, QueueProperties props) {
        this.sessionRepository = sessionRepository;
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

    public boolean extendTtl(Long userId) {
        return sessionRepository.extendTtl(
                userId,
                props.getHardTtlSeconds(),
                props.getActivityExtensionSeconds(),
                props.getMaxExtensionsPerMinute()
        );
    }

    public void removeExpiredTrackerEntries(double maxScore) {
        sessionRepository.removeExpiredTrackerEntries(maxScore);
    }

    public void clearTracker() {
        sessionRepository.clearTracker();
    }

    // Query

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

    public record SessionInfo(String status, String createdAt) {}
}
