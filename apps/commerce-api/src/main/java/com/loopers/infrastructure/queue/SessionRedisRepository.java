package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.SessionRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SessionRedisRepository implements SessionRepository {

    private static final String SESSION_KEY = "session:";
    private static final String SESSION_TRACKER_KEY = "session-tracker";
    private static final String EXTEND_RATE_KEY = "rate:extend:";

    private static final String CAS_LUA =
            "local status = redis.call('HGET', KEYS[1], 'status') " +
            "if status == false then return -1 end " +
            "if status == ARGV[1] then " +
            "  redis.call('HSET', KEYS[1], 'status', ARGV[2]) " +
            "  return 1 " +
            "end " +
            "return 0";

    private static final String EXTEND_TTL_LUA =
            "local createdAt = redis.call('HGET', KEYS[1], 'createdAt') " +
            "if createdAt == false then return 0 end " +
            "local now = tonumber(ARGV[2]) " +
            "if tonumber(createdAt) + tonumber(ARGV[5]) < now + tonumber(ARGV[6]) then return 0 end " +
            "redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1]) " +
            "local count = redis.call('ZCARD', KEYS[2]) " +
            "if count >= tonumber(ARGV[3]) then return 0 end " +
            "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[4]) " +
            "redis.call('EXPIRE', KEYS[2], 60) " +
            "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[6])) " +
            "return 1";

    private final RedisTemplate<String, String> masterRedisTemplate;

    public SessionRedisRepository(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    // Command

    @Override
    public void create(Long userId, int accessTtlSeconds, int hardTtlSeconds) {
        String key = sessionKey(userId);
        long now = Instant.now().getEpochSecond();

        masterRedisTemplate.opsForHash().putAll(key, Map.of(
                "status", "ACTIVE",
                "createdAt", String.valueOf(now)
        ));
        masterRedisTemplate.expire(key, Duration.ofSeconds(accessTtlSeconds));

        double hardDeadline = now + hardTtlSeconds;
        masterRedisTemplate.opsForZSet().add(SESSION_TRACKER_KEY, userId.toString(), hardDeadline);
    }

    @Override
    public long compareAndSwap(Long userId, String expectedStatus, String newStatus) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CAS_LUA, Long.class);
        Long result = masterRedisTemplate.execute(script,
                Collections.singletonList(sessionKey(userId)),
                expectedStatus, newStatus);

        return result != null ? result : -1;
    }

    @Override
    public void delete(Long userId) {
        masterRedisTemplate.delete(sessionKey(userId));
        masterRedisTemplate.opsForZSet().remove(SESSION_TRACKER_KEY, userId.toString());
    }

    @Override
    public boolean extendTtl(Long userId, int hardTtlSeconds, int extensionSeconds, int maxExtensionsPerMinute) {
        String sessionKey = sessionKey(userId);
        String rateKey = EXTEND_RATE_KEY + userId;
        long now = Instant.now().getEpochSecond();
        long windowStart = now - 60;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(EXTEND_TTL_LUA, Long.class);
        Long result = masterRedisTemplate.execute(script,
                List.of(sessionKey, rateKey),
                String.valueOf(windowStart),
                String.valueOf(now),
                String.valueOf(maxExtensionsPerMinute),
                UUID.randomUUID().toString(),
                String.valueOf(hardTtlSeconds),
                String.valueOf(extensionSeconds));

        return result != null && result == 1;
    }

    @Override
    public void removeExpiredTrackerEntries(double maxScore) {
        masterRedisTemplate.opsForZSet().removeRangeByScore(SESSION_TRACKER_KEY, 0, maxScore);
    }

    @Override
    public void clearTracker() {
        masterRedisTemplate.delete(SESSION_TRACKER_KEY);
    }

    // Query

    @Override
    public SessionData find(Long userId) {
        String key = sessionKey(userId);
        Map<Object, Object> entries = masterRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }

        String status = (String) entries.get("status");
        String createdAt = (String) entries.get("createdAt");
        return new SessionData(status, createdAt);
    }

    @Override
    public boolean exists(Long userId) {
        return Boolean.TRUE.equals(masterRedisTemplate.hasKey(sessionKey(userId)));
    }

    private String sessionKey(Long userId) {
        return SESSION_KEY + userId;
    }
}
