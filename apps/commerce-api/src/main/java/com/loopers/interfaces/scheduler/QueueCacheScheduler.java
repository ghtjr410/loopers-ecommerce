package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.SlotService;
import com.loopers.application.stock.StockService;
import com.loopers.interfaces.api.queue.filter.EarlyRejectionFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class QueueCacheScheduler {

    private static final String WAITING_QUEUE_KEY = "waiting-queue:";

    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final EarlyRejectionFilter earlyRejectionFilter;
    private final ModeManager modeManager;
    private final SlotService slotService;
    private final StockService stockService;

    public QueueCacheScheduler(
            RedisTemplate<String, String> defaultRedisTemplate,
            EarlyRejectionFilter earlyRejectionFilter,
            ModeManager modeManager,
            SlotService slotService,
            StockService stockService
    ) {
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.earlyRejectionFilter = earlyRejectionFilter;
        this.modeManager = modeManager;
        this.slotService = slotService;
        this.stockService = stockService;
    }

    @Scheduled(fixedRate = 2000)
    public void updateSoldOutFromDB() {
        if (modeManager.isHot()) return;
        try {
            Set<Long> soldOut = stockService.findSoldOutProductIds();
            earlyRejectionFilter.updateSoldOutProducts(soldOut);
        } catch (Exception e) {
            log.warn("매진 상품 캐시 갱신 실패", e);
        }
    }

    @Scheduled(fixedRate = 500)
    public void updateSlotRemaining() {
        if (!modeManager.isHot()) return;
        try {
            Map<Long, Long> remaining = new HashMap<>();
            for (Long productId : modeManager.getHotProductIds()) {
                remaining.put(productId, slotService.getAvailableCount(productId));
            }
            earlyRejectionFilter.updateSlotRemaining(remaining);
        } catch (Exception e) {
            log.warn("Slot 잔여 캐시 갱신 실패", e);
        }
    }

    @Scheduled(fixedRate = 2000)
    public void updateQueueSizes() {
        try {
            Map<Long, Long> sizes = new HashMap<>();
            for (Long productId : modeManager.getHotProductIds()) {
                Long size = defaultRedisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY + productId);
                sizes.put(productId, size != null ? size : 0);
            }
            earlyRejectionFilter.updateQueueSizes(sizes);
        } catch (Exception e) {
            log.warn("대기열 크기 캐시 갱신 실패", e);
        }
    }
}
