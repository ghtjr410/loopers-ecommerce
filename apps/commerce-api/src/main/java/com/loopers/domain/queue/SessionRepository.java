package com.loopers.domain.queue;

public interface SessionRepository {

    // Command

    void create(Long userId, int accessTtlSeconds, int hardTtlSeconds);

    /**
     * CAS: status가 expected와 일치하면 newStatus로 변경.
     * @return 1(성공), 0(상태 불일치), -1(키 없음/세션 만료)
     */
    long compareAndSwap(Long userId, String expectedStatus, String newStatus);

    void delete(Long userId);

    /**
     * Activity TTL 연장 — rate limit + Hard TTL + EXPIRE를 원자적으로 처리.
     * @return true면 연장 성공
     */
    boolean extendTtl(Long userId, int hardTtlSeconds, int extensionSeconds, int maxExtensionsPerMinute);

    void removeExpiredTrackerEntries(double maxScore);

    void clearTracker();

    // Query

    SessionData find(Long userId);

    boolean exists(Long userId);

    record SessionData(String status, String createdAt) {}
}
