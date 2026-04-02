package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.ModeManager;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
public class ProcessingRecoveryScheduler {

    private static final String PROCESSING_QUEUE_KEY = "processing-queue:";
    private static final String WAITING_QUEUE_KEY = "waiting-queue:";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final ModeManager modeManager;
    private final QueueProperties props;

    public ProcessingRecoveryScheduler(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            ModeManager modeManager,
            QueueProperties props
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.modeManager = modeManager;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${queue.processing-recovery-interval-ms:30000}")
    public void recoverStaleProcessing() {
        if (!modeManager.isHot() && !modeManager.isDrain()) {
            return;
        }

        for (Long productId : modeManager.getHotProductIds()) {
            try {
                recoverProduct(productId);
            } catch (Exception e) {
                log.warn("Processing 복구 실패: productId={}", productId, e);
            }
        }
    }

    private void recoverProduct(Long productId) {
        String processingKey = PROCESSING_QUEUE_KEY + productId;
        String waitingKey = WAITING_QUEUE_KEY + productId;
        double cutoff = Instant.now().minusSeconds(props.getProcessingTimeoutSeconds())
                .toEpochMilli() / 1000.0;

        Set<String> staleMembers = masterRedisTemplate.opsForZSet()
                .rangeByScore(processingKey, 0, cutoff);

        if (staleMembers == null || staleMembers.isEmpty()) {
            return;
        }

        for (String member : staleMembers) {
            String[] parts = member.split(":");
            if (parts.length != 2) continue;

            String userId = parts[0];
            double originalScore = Double.parseDouble(parts[1]);

            masterRedisTemplate.opsForZSet().add(waitingKey, userId, originalScore);
            masterRedisTemplate.opsForZSet().remove(processingKey, member);
            log.info("Processing 복구: productId={}, userId={}, 원래 순서로 복귀", productId, userId);
        }
    }
}
