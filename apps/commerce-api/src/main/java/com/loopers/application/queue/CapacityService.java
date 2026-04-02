package com.loopers.application.queue;

import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CapacityService {

    private static final String CAPACITY_KEY = "purchase-capacity:";
    private static final String TOKEN_TRACKER_KEY = "token-tracker:";
    private static final String TOKEN_CONSUMED_KEY = "token-consumed:";
    private static final String TOKEN_QUANTITY_KEY = "token-quantity:";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final QueueProperties props;

    public CapacityService(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            QueueProperties props
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.props = props;
    }

    public void initializeCapacity(Long productId, int available) {
        masterRedisTemplate.opsForValue().set(CAPACITY_KEY + productId, String.valueOf(available));
    }

    public long getRemaining(Long productId) {
        String val = masterRedisTemplate.opsForValue().get(CAPACITY_KEY + productId);
        return val != null ? Long.parseLong(val) : 0;
    }

    public boolean tryRestore(Long userId, Long productId) {
        String consumedKey = TOKEN_CONSUMED_KEY + userId + ":" + productId;
        if (Boolean.TRUE.equals(masterRedisTemplate.hasKey(consumedKey))) {
            return false;
        }
        String qtyKey = TOKEN_QUANTITY_KEY + userId + ":" + productId;
        String qtyStr = masterRedisTemplate.opsForValue().get(qtyKey);
        long quantity = qtyStr != null ? Long.parseLong(qtyStr) : 1;
        masterRedisTemplate.opsForValue().increment(CAPACITY_KEY + productId, quantity);
        masterRedisTemplate.delete(qtyKey);
        return true;
    }

    public void recordQuantity(Long userId, Long productId, int quantity) {
        masterRedisTemplate.opsForValue().set(
                TOKEN_QUANTITY_KEY + userId + ":" + productId,
                String.valueOf(quantity), Duration.ofSeconds(props.getConsumedTtlSeconds())
        );
    }

    public void deleteQuantity(Long userId, Long productId) {
        masterRedisTemplate.delete(TOKEN_QUANTITY_KEY + userId + ":" + productId);
    }

    public void markConsumed(Long userId, Long productId) {
        masterRedisTemplate.opsForValue().set(
                TOKEN_CONSUMED_KEY + userId + ":" + productId,
                "1", Duration.ofSeconds(props.getConsumedTtlSeconds())
        );
    }

    public void clearCapacity(Long productId) {
        masterRedisTemplate.delete(CAPACITY_KEY + productId);
        masterRedisTemplate.delete(TOKEN_TRACKER_KEY + productId);
    }
}
