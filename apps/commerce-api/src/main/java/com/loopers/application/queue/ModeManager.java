package com.loopers.application.queue;

import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ModeManager {

    public enum Mode { NORMAL, EVENT, DRAIN }

    public record ModeState(
            Mode mode,
            Instant graceDeadline
    ) {
        static ModeState normal() {
            return new ModeState(Mode.NORMAL, Instant.MIN);
        }

        boolean isEvent() { return mode == Mode.EVENT; }
        boolean isDrain() { return mode == Mode.DRAIN; }
        boolean isInGracePeriod() { return mode == Mode.DRAIN && Instant.now().isBefore(graceDeadline); }
    }

    private final QueueProperties queueProperties;
    private volatile ModeState state = ModeState.normal();

    public ModeManager(QueueProperties queueProperties) {
        this.queueProperties = queueProperties;
    }

    public ModeState getState() { return state; }

    // 편의 메서드 — state에 위임

    public boolean isEvent() { return state.isEvent(); }
    public boolean isDrain() { return state.isDrain(); }
    public boolean isInGracePeriod() { return state.isInGracePeriod(); }

    // 원자적 전환 — volatile write 1회

    public void switchToEvent() {
        this.state = new ModeState(Mode.EVENT, Instant.MIN);
    }

    public void switchToDrain() {
        this.state = new ModeState(
                Mode.DRAIN,
                Instant.now().plusSeconds(queueProperties.getGracePeriodSeconds())
        );
    }

    public void switchToNormal() {
        this.state = ModeState.normal();
    }
}
