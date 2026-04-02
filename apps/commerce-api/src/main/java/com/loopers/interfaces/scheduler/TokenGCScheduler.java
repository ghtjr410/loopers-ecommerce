package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.SlotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
public class TokenGCScheduler {

    private static final String SLOT_EXPIRY_KEY = "slot-expiry:";
    private static final String ENTRY_TOKEN_KEY = "entry-token:";
    private static final String SLOT_ASSIGNMENT_KEY = "slot-assignment:";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final SlotService slotService;
    private final ModeManager modeManager;

    public TokenGCScheduler(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            SlotService slotService,
            ModeManager modeManager
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.slotService = slotService;
        this.modeManager = modeManager;
    }

    @Scheduled(fixedDelayString = "${queue.gc-interval-ms:10000}")
    public void cleanExpiredTokens() {
        if (!modeManager.isHot() && !modeManager.isDrain()) {
            return;
        }

        for (Long productId : modeManager.getHotProductIds()) {
            try {
                processExpiredSlots(productId);
            } catch (Exception e) {
                log.warn("TokenGC 실패: productId={}", productId, e);
            }
        }
    }

    private void processExpiredSlots(Long productId) {
        String expiryKey = SLOT_EXPIRY_KEY + productId;
        double now = Instant.now().toEpochMilli() / 1000.0;

        Set<String> expiredMembers = masterRedisTemplate.opsForZSet()
                .rangeByScore(expiryKey, 0, now);

        if (expiredMembers == null || expiredMembers.isEmpty()) {
            return;
        }

        for (String member : expiredMembers) {
            String[] parts = member.split(":");
            if (parts.length != 2) continue;

            Long userId = Long.parseLong(parts[0]);
            String slotId = parts[1];

            Boolean tokenExists = masterRedisTemplate.hasKey(
                    ENTRY_TOKEN_KEY + userId + ":" + productId);

            if (Boolean.FALSE.equals(tokenExists)) {
                slotService.releaseSlot(productId, slotId);
                masterRedisTemplate.opsForZSet().remove(expiryKey, member);
                masterRedisTemplate.delete(SLOT_ASSIGNMENT_KEY + userId + ":" + productId);
                log.debug("Slot 회수: productId={}, userId={}, slotId={}", productId, userId, slotId);
            }
        }
    }
}
