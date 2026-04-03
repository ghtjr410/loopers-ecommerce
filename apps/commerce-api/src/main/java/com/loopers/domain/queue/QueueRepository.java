package com.loopers.domain.queue;

import java.util.Set;

public interface QueueRepository {

    // Command

    /**
     * 대기열에 유저 추가. 기존 멤버는 score만 갱신, 신규 멤버는 크기 상한 체크.
     * @return true면 추가 성공, false면 만석
     */
    boolean enqueue(Long userId, int maxSize);

    void dequeue(String... userIds);

    // Query

    Set<String> peekTop(int count);

    Long getRank(Long userId);

    long size();
}
