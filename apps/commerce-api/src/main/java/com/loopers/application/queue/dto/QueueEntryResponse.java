package com.loopers.application.queue.dto;

public record QueueEntryResponse(
        long position,
        Long estimatedWaitSeconds,
        String status
) {
    public static QueueEntryResponse waiting(long position, long estimatedWaitSeconds) {
        return new QueueEntryResponse(position, estimatedWaitSeconds, "WAITING");
    }
}
