package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.QueueService;
import com.loopers.interfaces.api.queue.filter.EarlyRejectionFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QueueCacheScheduler {

    private final QueueService queueService;
    private final EarlyRejectionFilter earlyRejectionFilter;
    private final ModeManager modeManager;
    private final MeterRegistry meterRegistry;

    public QueueCacheScheduler(
            QueueService queueService,
            EarlyRejectionFilter earlyRejectionFilter,
            ModeManager modeManager,
            MeterRegistry meterRegistry
    ) {
        this.queueService = queueService;
        this.earlyRejectionFilter = earlyRejectionFilter;
        this.modeManager = modeManager;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedRate = 2000)
    public void updateQueueSize() {
        if (!modeManager.isEvent() && !modeManager.isDrain()) {
            return;
        }
        try {
            long size = queueService.getQueueSize();
            earlyRejectionFilter.updateQueueSize(size);
            meterRegistry.gauge("queue.depth", Tags.empty(), size);
        } catch (Exception e) {
            log.warn("대기열 크기 캐시 갱신 실패", e);
        }
    }
}
