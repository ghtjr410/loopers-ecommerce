package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

@Repository
public class QueueRedisRepository implements QueueRepository {

    private static final String WAITING_QUEUE_KEY = "waiting-queue:bf-2025";

    private static final String ZADD_WITH_LIMIT_LUA =
            "local exists = redis.call('ZSCORE', KEYS[1], ARGV[3]) " +
            "if exists then " +
            "  redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]) " +
            "  return 1 " +
            "end " +
            "local size = redis.call('ZCARD', KEYS[1]) " +
            "if size >= tonumber(ARGV[1]) then return 0 end " +
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]) " +
            "return 1";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> defaultRedisTemplate;

    public QueueRedisRepository(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> defaultRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.defaultRedisTemplate = defaultRedisTemplate;
    }

    // Command

    @Override
    public boolean enqueue(Long userId, int maxSize) {
        double score = Instant.now().toEpochMilli() / 1000.0;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ZADD_WITH_LIMIT_LUA, Long.class);
        Long result = masterRedisTemplate.execute(script,
                Collections.singletonList(WAITING_QUEUE_KEY),
                String.valueOf(maxSize),
                String.valueOf(score),
                userId.toString());

        return result != null && result == 1;
    }

    @Override
    public void dequeue(String... userIds) {
        masterRedisTemplate.opsForZSet().remove(WAITING_QUEUE_KEY, (Object[]) userIds);
    }

    // Query

    @Override
    public Set<String> peekTop(int count) {
        return masterRedisTemplate.opsForZSet().range(WAITING_QUEUE_KEY, 0, count - 1);
    }

    @Override
    public Long getRank(Long userId) {
        Long rank = defaultRedisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userId.toString());
        return rank != null ? rank + 1 : null;
    }

    @Override
    public long size() {
        Long size = defaultRedisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY);
        return size != null ? size : 0;
    }
}
