package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.RedisLockRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RedisLockRepositoryImpl implements RedisLockRepository {

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisLockRepositoryImpl(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, int ttlSeconds) {
        Boolean result = masterRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(result);
    }
}
