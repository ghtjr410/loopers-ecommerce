package com.loopers.application.queue;

import com.loopers.application.queue.dto.QueueEntryResponse;
import com.loopers.application.queue.dto.QueuePositionResponse;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import com.loopers.interfaces.scheduler.PositionCacheScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.script.DefaultRedisScript;

@Service
public class QueueService {

    private static final String ENTRY_TOKEN_KEY = "entry-token:";
    private static final String CAPACITY_KEY = "purchase-capacity:";
    private static final String TOKEN_TRACKER_KEY = "token-tracker:";
    private static final String WAITING_QUEUE_KEY = "waiting-queue:";
    private static final String EXTEND_RATE_KEY = "rate:extend:";

    private static final String EXTEND_RATE_LUA =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]) " +
            "local count = redis.call('ZCARD', KEYS[1]) " +
            "if count >= tonumber(ARGV[3]) then return 0 end " +
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4]) " +
            "redis.call('EXPIRE', KEYS[1], 60) " +
            "return 1";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final ModeManager modeManager;
    private final QueueProperties props;
    private final PositionCacheScheduler positionCacheScheduler;

    public QueueService(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> defaultRedisTemplate,
            ModeManager modeManager,
            QueueProperties props,
            PositionCacheScheduler positionCacheScheduler
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.modeManager = modeManager;
        this.props = props;
        this.positionCacheScheduler = positionCacheScheduler;
    }

    // Command

    public QueueEntryResponse enter(Long userId, Long productId, int quantity) {
        if (!modeManager.isHotProduct(productId)) {
            return issueImmediateToken(userId, productId);
        }
        return enterHotQueue(userId, productId);
    }

    public void issueToken(Long userId, Long productId) {
        String token = UUID.randomUUID().toString();
        masterRedisTemplate.opsForValue().set(
                tokenKey(userId, productId), token, props.getAccessTtlSeconds(), TimeUnit.SECONDS
        );

        double expiryScore = Instant.now().plusSeconds(props.getHardTtlSeconds()).toEpochMilli() / 1000.0;
        masterRedisTemplate.opsForZSet().add(
                TOKEN_TRACKER_KEY + productId, userId.toString(), expiryScore
        );
    }

    public boolean extendTokenTtl(Long userId, Long productId) {
        if (!modeManager.isHotProduct(productId)) {
            return false;
        }

        String tokenKey = tokenKey(userId, productId);
        String trackerKey = TOKEN_TRACKER_KEY + productId;

        Double expiryScore = masterRedisTemplate.opsForZSet().score(trackerKey, userId.toString());
        if (expiryScore == null) {
            return false;
        }

        double issuedAt = expiryScore - props.getHardTtlSeconds();
        double now = Instant.now().toEpochMilli() / 1000.0;
        if (issuedAt + props.getHardTtlSeconds() < now + props.getActivityExtensionSeconds()) {
            return false;
        }

        if (!checkExtendRateLimit(userId, productId)) {
            return false;
        }

        masterRedisTemplate.expire(tokenKey, Duration.ofSeconds(props.getActivityExtensionSeconds()));
        return true;
    }

    private boolean checkExtendRateLimit(Long userId, Long productId) {
        String key = EXTEND_RATE_KEY + userId + ":" + productId;
        double now = System.currentTimeMillis() / 1000.0;
        double windowStart = now - 60;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(EXTEND_RATE_LUA, Long.class);
        Long allowed = masterRedisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(windowStart),
                String.valueOf(now),
                String.valueOf(props.getMaxExtensionsPerMinute()),
                UUID.randomUUID().toString());

        return allowed != null && allowed == 1;
    }

    public void deleteToken(Long userId, Long productId) {
        masterRedisTemplate.delete(tokenKey(userId, productId));
        masterRedisTemplate.opsForZSet().remove(TOKEN_TRACKER_KEY + productId, userId.toString());
    }

    // Query

    public boolean validateToken(Long userId, Long productId, String token) {
        String stored = masterRedisTemplate.opsForValue().get(tokenKey(userId, productId));
        return token.equals(stored);
    }

    public QueuePositionResponse getPosition(Long userId, Long productId) {
        String token = masterRedisTemplate.opsForValue().get(tokenKey(userId, productId));
        if (token != null) {
            return QueuePositionResponse.ready(token);
        }

        Long cachedPosition = positionCacheScheduler.getCachedPosition(productId, userId);
        if (cachedPosition != null) {
            return QueuePositionResponse.waiting(cachedPosition, estimateWaitSeconds(cachedPosition));
        }

        Long rank = defaultRedisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY + productId, userId.toString());
        if (rank == null) {
            return QueuePositionResponse.notInQueue();
        }

        long position = rank + 1;
        long estimatedWait = estimateWaitSeconds(position);
        return QueuePositionResponse.waiting(position, estimatedWait);
    }

    private QueueEntryResponse issueImmediateToken(Long userId, Long productId) {
        String token = UUID.randomUUID().toString();
        masterRedisTemplate.opsForValue().set(
                tokenKey(userId, productId), token, props.getAccessTtlSeconds(), TimeUnit.SECONDS
        );
        return QueueEntryResponse.immediate(token);
    }

    private QueueEntryResponse enterHotQueue(Long userId, Long productId) {
        String waitingKey = WAITING_QUEUE_KEY + productId;
        Long queueSize = masterRedisTemplate.opsForZSet().zCard(waitingKey);

        if (queueSize != null && queueSize == 0) {
            Long cap = masterRedisTemplate.opsForValue().decrement(CAPACITY_KEY + productId);
            if (cap != null && cap >= 0) {
                String token = UUID.randomUUID().toString();
                masterRedisTemplate.opsForValue().set(
                        tokenKey(userId, productId), token, props.getAccessTtlSeconds(), TimeUnit.SECONDS
                );
                double expiryScore = Instant.now().plusSeconds(props.getHardTtlSeconds()).toEpochMilli() / 1000.0;
                masterRedisTemplate.opsForZSet().add(
                        TOKEN_TRACKER_KEY + productId, userId.toString(), expiryScore
                );
                return QueueEntryResponse.immediate(token);
            }
            // 롤백
            masterRedisTemplate.opsForValue().increment(CAPACITY_KEY + productId);
        }

        double score = Instant.now().toEpochMilli() / 1000.0;
        masterRedisTemplate.opsForZSet().addIfAbsent(waitingKey, userId.toString(), score);

        Long rank = masterRedisTemplate.opsForZSet().rank(waitingKey, userId.toString());
        long position = (rank != null) ? rank + 1 : 1;
        long estimatedWait = estimateWaitSeconds(position);
        return QueueEntryResponse.waiting(position, estimatedWait);
    }

    private long estimateWaitSeconds(long position) {
        return Math.max(1, position / 424);
    }

    private String tokenKey(Long userId, Long productId) {
        return ENTRY_TOKEN_KEY + userId + ":" + productId;
    }
}
