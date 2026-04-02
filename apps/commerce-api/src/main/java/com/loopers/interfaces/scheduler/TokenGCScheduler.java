package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.CapacityService;
import com.loopers.application.queue.ModeManager;
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

    private static final String TOKEN_TRACKER_KEY = "token-tracker:";
    private static final String ENTRY_TOKEN_KEY = "entry-token:";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final CapacityService capacityService;
    private final ModeManager modeManager;

    public TokenGCScheduler(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            CapacityService capacityService,
            ModeManager modeManager
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.capacityService = capacityService;
        this.modeManager = modeManager;
    }

    @Scheduled(fixedDelayString = "${queue.gc-interval-ms:10000}")
    public void cleanExpiredTokens() {
        if (!modeManager.isHot() && !modeManager.isDrain()) {
            return;
        }

        for (Long productId : modeManager.getHotProductIds()) {
            try {
                processExpiredTokens(productId);
            } catch (Exception e) {
                log.warn("TokenGC 실패: productId={}", productId, e);
            }
        }
    }

    private void processExpiredTokens(Long productId) {
        String trackerKey = TOKEN_TRACKER_KEY + productId;
        double now = Instant.now().toEpochMilli() / 1000.0;

        Set<String> expiredMembers = masterRedisTemplate.opsForZSet()
                .rangeByScore(trackerKey, 0, now);

        if (expiredMembers == null || expiredMembers.isEmpty()) {
            return;
        }

        for (String userId : expiredMembers) {
            Long userIdLong = Long.parseLong(userId);

            Boolean tokenExists = masterRedisTemplate.hasKey(
                    ENTRY_TOKEN_KEY + userId + ":" + productId);

            if (Boolean.FALSE.equals(tokenExists)) {
                boolean restored = capacityService.tryRestore(userIdLong, productId);
                masterRedisTemplate.opsForZSet().remove(trackerKey, userId);
                if (restored) {
                    log.debug("용량 복구: productId={}, userId={}", productId, userId);
                } else {
                    log.debug("결제 완료 건 skip: productId={}, userId={}", productId, userId);
                }
            }
        }
    }
}
