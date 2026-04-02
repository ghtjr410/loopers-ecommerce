package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.ModeManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PositionCacheScheduler {

    private static final String WAITING_QUEUE_KEY = "waiting-queue:";

    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final ModeManager modeManager;

    private final Map<Long, Map<String, Long>> rankCache = new ConcurrentHashMap<>();

    public PositionCacheScheduler(
            RedisTemplate<String, String> defaultRedisTemplate,
            ModeManager modeManager
    ) {
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.modeManager = modeManager;
    }

    @Scheduled(fixedRate = 1000)
    public void refreshRankCache() {
        if (!modeManager.isHot()) {
            rankCache.clear();
            return;
        }

        for (Long productId : modeManager.getHotProductIds()) {
            try {
                Set<String> members = defaultRedisTemplate.opsForZSet()
                        .range(WAITING_QUEUE_KEY + productId, 0, -1);

                if (members == null || members.isEmpty()) {
                    rankCache.put(productId, Map.of());
                    continue;
                }

                Map<String, Long> positions = new ConcurrentHashMap<>();
                long rank = 0;
                for (String member : members) {
                    positions.put(member, ++rank);
                }
                rankCache.put(productId, positions);
            } catch (Exception e) {
                log.warn("순번 캐시 갱신 실패: productId={}", productId, e);
            }
        }
    }

    public Long getCachedPosition(Long productId, Long userId) {
        Map<String, Long> positions = rankCache.get(productId);
        if (positions == null) return null;
        return positions.get(userId.toString());
    }
}
