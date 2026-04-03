package com.loopers.interfaces.api.queue;

import jakarta.validation.constraints.NotNull;

public record QueueRequest() {

    // Command

    public record ModeChange(
            @NotNull Mode mode
    ) {
        public enum Mode { EVENT, DRAIN, NORMAL }
    }
}
