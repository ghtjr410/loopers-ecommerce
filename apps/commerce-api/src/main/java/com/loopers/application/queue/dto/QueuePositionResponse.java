package com.loopers.application.queue.dto;

public record QueuePositionResponse(
        long position,
        String token,
        Long estimatedWaitSeconds
) {
    public static QueuePositionResponse ready(String token) {
        return new QueuePositionResponse(0, token, null);
    }

    public static QueuePositionResponse waiting(long position, long estimatedWaitSeconds) {
        return new QueuePositionResponse(position, null, estimatedWaitSeconds);
    }

    public static QueuePositionResponse notInQueue() {
        return new QueuePositionResponse(-1, null, null);
    }
}
