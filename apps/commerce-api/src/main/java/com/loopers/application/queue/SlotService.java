package com.loopers.application.queue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class SlotService {

    private static final String SLOTS_AVAILABLE_KEY = "slots:available:";
    private static final String SLOT_EXPIRY_KEY = "slot-expiry:";

    private final RedisTemplate<String, String> masterRedisTemplate;

    public SlotService(@Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    public int initializeSlots(Long productId, int available, int maxQuantityPerUser) {
        int slotCount = available / maxQuantityPerUser;
        String key = SLOTS_AVAILABLE_KEY + productId;
        masterRedisTemplate.delete(key);
        String[] slots = IntStream.rangeClosed(1, slotCount)
                .mapToObj(i -> "slot-" + i)
                .toArray(String[]::new);
        if (slots.length > 0) {
            masterRedisTemplate.opsForSet().add(key, slots);
        }
        return slotCount;
    }

    public String acquireSlot(Long productId) {
        return masterRedisTemplate.opsForSet().pop(SLOTS_AVAILABLE_KEY + productId);
    }

    public void releaseSlot(Long productId, String slotId) {
        masterRedisTemplate.opsForSet().add(SLOTS_AVAILABLE_KEY + productId, slotId);
    }

    public long getAvailableCount(Long productId) {
        Long size = masterRedisTemplate.opsForSet().size(SLOTS_AVAILABLE_KEY + productId);
        return size != null ? size : 0;
    }

    public void clearSlots(Long productId) {
        masterRedisTemplate.delete(SLOTS_AVAILABLE_KEY + productId);
        masterRedisTemplate.delete(SLOT_EXPIRY_KEY + productId);
    }
}
