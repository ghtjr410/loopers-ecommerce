package com.loopers.application.queue;

import com.loopers.interfaces.api.queue.config.QueueProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class QueueWorker {

    private static final String WAITING_QUEUE_KEY = "waiting-queue:";
    private static final String PROCESSING_QUEUE_KEY = "processing-queue:";
    private static final String CAPACITY_KEY = "purchase-capacity:";
    private static final String ENTRY_TOKEN_KEY = "entry-token:";
    private static final String TOKEN_TRACKER_KEY = "token-tracker:";
    private static final String TOKEN_QUANTITY_KEY = "token-quantity:";

    private static final String WORKER_LUA =
            "local result = redis.call('ZPOPMIN', KEYS[1]) " +
            "if #result == 0 then return nil end " +
            "local userId = result[1] " +
            "local originalScore = result[2] " +
            "local member = userId .. ':' .. originalScore " +
            "redis.call('ZADD', KEYS[2], ARGV[1], member) " +
            "return {userId, originalScore}";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final ModeManager modeManager;
    private final QueueProperties props;

    private volatile boolean running = true;

    public QueueWorker(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            ModeManager modeManager,
            QueueProperties props
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
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

        DefaultRedisScript<List> script = new DefaultRedisScript<>(WORKER_LUA, List.class);
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

        // 수량 조회 (enter() 시 저장됨)
        String qtyKey = TOKEN_QUANTITY_KEY + userIdLong + ":" + productId;
        String qtyStr = masterRedisTemplate.opsForValue().get(qtyKey);
        int quantity = qtyStr != null ? Integer.parseInt(qtyStr) : 1;

        // 용량 확인 (DECRBY — Redis 원자적 명령, Lua 밖)
        Long remaining = masterRedisTemplate.opsForValue()
                .decrement(CAPACITY_KEY + productId, quantity);
        if (remaining != null && remaining < 0) {
            masterRedisTemplate.opsForValue()
                    .increment(CAPACITY_KEY + productId, quantity);
            masterRedisTemplate.opsForZSet()
                    .add(waitingKey, userId, Double.parseDouble(originalScore));
            masterRedisTemplate.opsForZSet().remove(processingKey, processingMember);
            return false;
        }

        try {
            String token = UUID.randomUUID().toString();
            masterRedisTemplate.opsForValue().set(
                    ENTRY_TOKEN_KEY + userIdLong + ":" + productId,
                    token, Duration.ofSeconds(props.getAccessTtlSeconds())
            );

            double expiryTime = System.currentTimeMillis() / 1000.0 + props.getHardTtlSeconds();
            masterRedisTemplate.opsForZSet().add(
                    TOKEN_TRACKER_KEY + productId, userId, expiryTime
            );

            masterRedisTemplate.opsForZSet().remove(processingKey, processingMember);
        } catch (Exception e) {
            log.error("토큰 발급 실패. userId={}, productId={}. Recovery 예정.", userIdLong, productId, e);
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
