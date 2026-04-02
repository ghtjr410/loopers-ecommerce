package com.loopers.application.queue;

import com.loopers.application.queue.dto.QueueEntryResponse;
import com.loopers.application.queue.dto.QueuePositionResponse;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class QueueService {

    private static final String ENTRY_TOKEN_KEY = "entry-token:";
    private static final String SLOT_ASSIGNMENT_KEY = "slot-assignment:";
    private static final String SLOT_EXPIRY_KEY = "slot-expiry:";
    private static final String WAITING_QUEUE_KEY = "waiting-queue:";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final ModeManager modeManager;
    private final SlotService slotService;
    private final QueueProperties props;

    public QueueService(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> defaultRedisTemplate,
            ModeManager modeManager,
            SlotService slotService,
            QueueProperties props
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.modeManager = modeManager;
        this.slotService = slotService;
        this.props = props;
    }

    // Command

    public QueueEntryResponse enter(Long userId, Long productId, int quantity) {
        if (!modeManager.isHotProduct(productId)) {
            return issueImmediateToken(userId, productId);
        }
        return enterHotQueue(userId, productId);
    }

    public void issueToken(Long userId, Long productId, String slotId) {
        String token = UUID.randomUUID().toString();
        String tokenKey = tokenKey(userId, productId);
        int accessTtl = props.getAccessTtlSeconds();
        int hardTtl = props.getHardTtlSeconds();

        masterRedisTemplate.opsForValue().set(tokenKey, token, accessTtl, TimeUnit.SECONDS);
        masterRedisTemplate.opsForValue().set(
                slotAssignmentKey(userId, productId), slotId, hardTtl, TimeUnit.SECONDS
        );

        double expiryScore = Instant.now().plusSeconds(hardTtl).toEpochMilli() / 1000.0;
        String expiryMember = userId + ":" + slotId;
        masterRedisTemplate.opsForZSet().add(SLOT_EXPIRY_KEY + productId, expiryMember, expiryScore);
    }

    public boolean extendTokenTtl(Long userId, Long productId) {
        if (!modeManager.isHotProduct(productId)) {
            return false;
        }

        String tokenKey = tokenKey(userId, productId);
        String slotExpiryKey = SLOT_EXPIRY_KEY + productId;

        Set<String> members = masterRedisTemplate.opsForZSet().rangeByScore(
                slotExpiryKey,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
        );
        if (members == null) {
            return false;
        }

        String prefix = userId + ":";
        Double issuedScore = null;
        for (String member : members) {
            if (member.startsWith(prefix)) {
                issuedScore = masterRedisTemplate.opsForZSet().score(slotExpiryKey, member);
                break;
            }
        }
        if (issuedScore == null) {
            return false;
        }

        double issuedAt = issuedScore - props.getHardTtlSeconds();
        double now = Instant.now().toEpochMilli() / 1000.0;
        if (issuedAt + props.getHardTtlSeconds() < now + props.getActivityExtensionSeconds()) {
            return false;
        }

        masterRedisTemplate.expire(tokenKey, Duration.ofSeconds(props.getActivityExtensionSeconds()));
        return true;
    }

    public void deleteToken(Long userId, Long productId) {
        masterRedisTemplate.delete(tokenKey(userId, productId));
        masterRedisTemplate.delete(slotAssignmentKey(userId, productId));

        String slotExpiryKey = SLOT_EXPIRY_KEY + productId;
        Set<String> members = masterRedisTemplate.opsForZSet().rangeByScore(
                slotExpiryKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
        );
        if (members != null) {
            String prefix = userId + ":";
            members.stream()
                    .filter(m -> m.startsWith(prefix))
                    .forEach(m -> masterRedisTemplate.opsForZSet().remove(slotExpiryKey, m));
        }
    }

    // Query

    public boolean validateToken(Long userId, Long productId, String token) {
        String stored = masterRedisTemplate.opsForValue().get(tokenKey(userId, productId));
        return token.equals(stored);
    }

    public QueuePositionResponse getPosition(Long userId, Long productId) {
        String token = masterRedisTemplate.opsForValue().get(tokenKey(userId, productId));
        if (token != null) {
            return QueuePositionResponse.ready(token);
        }

        Long rank = defaultRedisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY + productId, userId.toString());
        if (rank == null) {
            return QueuePositionResponse.notInQueue();
        }

        long position = rank + 1;
        long estimatedWait = estimateWaitSeconds(position);
        return QueuePositionResponse.waiting(position, estimatedWait);
    }

    private QueueEntryResponse issueImmediateToken(Long userId, Long productId) {
        String token = UUID.randomUUID().toString();
        String tokenKey = tokenKey(userId, productId);
        masterRedisTemplate.opsForValue().set(tokenKey, token, props.getAccessTtlSeconds(), TimeUnit.SECONDS);
        return QueueEntryResponse.immediate(token);
    }

    private QueueEntryResponse enterHotQueue(Long userId, Long productId) {
        String waitingKey = WAITING_QUEUE_KEY + productId;
        Long queueSize = masterRedisTemplate.opsForZSet().zCard(waitingKey);

        if (queueSize != null && queueSize == 0) {
            String slotId = slotService.acquireSlot(productId);
            if (slotId != null) {
                String token = UUID.randomUUID().toString();
                String tokenKey = tokenKey(userId, productId);
                int accessTtl = props.getAccessTtlSeconds();
                int hardTtl = props.getHardTtlSeconds();

                masterRedisTemplate.opsForValue().set(tokenKey, token, accessTtl, TimeUnit.SECONDS);
                masterRedisTemplate.opsForValue().set(
                        slotAssignmentKey(userId, productId), slotId, hardTtl, TimeUnit.SECONDS
                );

                double expiryScore = Instant.now().plusSeconds(hardTtl).toEpochMilli() / 1000.0;
                masterRedisTemplate.opsForZSet().add(
                        SLOT_EXPIRY_KEY + productId, userId + ":" + slotId, expiryScore
                );

                return QueueEntryResponse.immediate(token);
            }
        }

        double score = Instant.now().toEpochMilli() / 1000.0;
        masterRedisTemplate.opsForZSet().addIfAbsent(waitingKey, userId.toString(), score);

        Long rank = masterRedisTemplate.opsForZSet().rank(waitingKey, userId.toString());
        long position = (rank != null) ? rank + 1 : 1;
        long estimatedWait = estimateWaitSeconds(position);
        return QueueEntryResponse.waiting(position, estimatedWait);
    }

    private long estimateWaitSeconds(long position) {
        return Math.max(1, position / 424);
    }

    private String tokenKey(Long userId, Long productId) {
        return ENTRY_TOKEN_KEY + userId + ":" + productId;
    }

    private String slotAssignmentKey(Long userId, Long productId) {
        return SLOT_ASSIGNMENT_KEY + userId + ":" + productId;
    }
}
