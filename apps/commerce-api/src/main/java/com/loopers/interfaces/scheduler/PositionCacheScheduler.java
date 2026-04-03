package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.ModeManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 순번 폴링 부하 감소용 인메모리 캐시.
 * 1초마다 ZRANGE → 인메모리 Map 갱신.
 * N명의 폴링을 1회 ZRANGE로 흡수 (Request Coalescing).
 */
@Slf4j
@Component
public class PositionCacheScheduler {

    private static final String WAITING_QUEUE_KEY = "waiting-queue:bf-2025";

    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final ModeManager modeManager;

    private volatile Map<String, Long> rankCache = Map.of();

    public PositionCacheScheduler(
            RedisTemplate<String, String> defaultRedisTemplate,
            ModeManager modeManager
    ) {
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.modeManager = modeManager;
    }

    @Scheduled(fixedRate = 1000)
    public void refreshRankCache() {
        if (!modeManager.isEvent()) {
            rankCache = Map.of();
            return;
        }

        try {
            Set<String> members = defaultRedisTemplate.opsForZSet().range(WAITING_QUEUE_KEY, 0, -1);

            if (members == null || members.isEmpty()) {
                rankCache = Map.of();
                return;
            }

            Map<String, Long> positions = new ConcurrentHashMap<>();
            long rank = 0;
            for (String member : members) {
                positions.put(member, ++rank);
            }
            rankCache = positions;
        } catch (Exception e) {
            log.warn("순번 캐시 갱신 실패", e);
        }
    }

    public Long getCachedPosition(Long userId) {
        return rankCache.get(userId.toString());
    }
}
