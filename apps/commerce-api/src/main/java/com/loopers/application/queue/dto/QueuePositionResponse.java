package com.loopers.application.queue.dto;

public record QueuePositionResponse(
        long position,
        Long estimatedWaitSeconds,
        String status
) {
    public static QueuePositionResponse ready() {
        return new QueuePositionResponse(0, null, "READY");
    }

    public static QueuePositionResponse waiting(long position, long estimatedWaitSeconds) {
        return new QueuePositionResponse(position, estimatedWaitSeconds, "WAITING");
    }

    public static QueuePositionResponse notInQueue() {
        return new QueuePositionResponse(-1, null, "NOT_IN_QUEUE");
    }

    public static QueuePositionResponse eventEnded() {
        return new QueuePositionResponse(-1, null, "EVENT_ENDED");
    }
}
