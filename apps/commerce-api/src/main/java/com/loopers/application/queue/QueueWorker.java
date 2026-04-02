package com.loopers.application.queue;

import com.loopers.interfaces.api.queue.config.QueueProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class QueueWorker {

    private static final String WAITING_QUEUE_KEY = "waiting-queue:";
    private static final String PROCESSING_QUEUE_KEY = "processing-queue:";

    private static final String ZPOPMIN_TO_PROCESSING_LUA =
            "local result = redis.call('ZPOPMIN', KEYS[1]) " +
            "if #result == 0 then return nil end " +
            "local userId = result[1] " +
            "local originalScore = result[2] " +
            "local member = userId .. ':' .. originalScore " +
            "redis.call('ZADD', KEYS[2], ARGV[1], member) " +
            "return {userId, originalScore}";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final QueueService queueService;
    private final SlotService slotService;
    private final ModeManager modeManager;
    private final QueueProperties props;

    private volatile boolean running = true;

    public QueueWorker(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            QueueService queueService,
            SlotService slotService,
            ModeManager modeManager,
            QueueProperties props
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.queueService = queueService;
        this.slotService = slotService;
        this.modeManager = modeManager;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        Thread workerThread = new Thread(this::runLoop, "queue-worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
    }

    private void runLoop() {
        while (running) {
            try {
                if (!modeManager.isHot()) {
                    sleep(props.getWorkerEmptyQueueSleepMs());
                    continue;
                }

                boolean processed = false;
                for (Long productId : modeManager.getHotProductIds()) {
                    if (processOne(productId)) {
                        processed = true;
                    }
                }

                if (!processed) {
                    sleep(props.getWorkerEmptyQueueSleepMs());
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
                log.error("Worker 오류, 재시도", e);
                sleep(props.getWorkerEmptyQueueSleepMs());
            }
        }
    }

    private boolean processOne(Long productId) {
        String waitingKey = WAITING_QUEUE_KEY + productId;
        String processingKey = PROCESSING_QUEUE_KEY + productId;
        double now = Instant.now().toEpochMilli() / 1000.0;

        DefaultRedisScript<List> script = new DefaultRedisScript<>(ZPOPMIN_TO_PROCESSING_LUA, List.class);
        List<?> result = masterRedisTemplate.execute(script,
                Arrays.asList(waitingKey, processingKey),
                String.valueOf(now));

        if (result == null || result.isEmpty()) {
            return false;
        }

        String userId = result.get(0).toString();
        String originalScore = result.get(1).toString();
        Long userIdLong = Long.parseLong(userId);
        String processingMember = userId + ":" + originalScore;

        String slotId = slotService.acquireSlot(productId);

        if (slotId != null) {
            queueService.issueToken(userIdLong, productId, slotId);
            masterRedisTemplate.opsForZSet().remove(processingKey, processingMember);
        } else {
            masterRedisTemplate.opsForZSet().add(waitingKey, userId, Double.parseDouble(originalScore));
            masterRedisTemplate.opsForZSet().remove(processingKey, processingMember);
        }

        return true;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
