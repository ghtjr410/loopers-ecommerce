package com.loopers.application.queue.dto;

public record QueueEntryResponse(
        long position,
        String token,
        Long estimatedWaitSeconds
) {
    public static QueueEntryResponse immediate(String token) {
        return new QueueEntryResponse(0, token, null);
    }

    public static QueueEntryResponse waiting(long position, long estimatedWaitSeconds) {
        return new QueueEntryResponse(position, null, estimatedWaitSeconds);
    }
}
