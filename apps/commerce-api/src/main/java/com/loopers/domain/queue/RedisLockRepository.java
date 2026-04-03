package com.loopers.domain.queue;

public interface RedisLockRepository {

    /**
     * 분산 락 획득 시도.
     * @return true면 락 획득 성공
     */
    boolean tryLock(String lockKey, int ttlSeconds);
}
