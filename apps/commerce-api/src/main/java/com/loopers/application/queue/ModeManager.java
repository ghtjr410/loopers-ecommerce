package com.loopers.application.queue;

import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class ModeManager {

    public enum Mode { NORMAL, HOT, DRAIN }

    public record ModeState(
            Mode mode,
            Set<Long> hotProductIds,
            Map<Long, Integer> maxQuantityPerUserMap,
            Instant graceDeadline
    ) {
        static ModeState normal() {
            return new ModeState(Mode.NORMAL, Set.of(), Map.of(), Instant.MIN);
        }

        boolean isHot() { return mode == Mode.HOT; }
        boolean isDrain() { return mode == Mode.DRAIN; }
        boolean isHotProduct(Long productId) { return hotProductIds.contains(productId); }
        boolean isInGracePeriod() { return Instant.now().isBefore(graceDeadline); }

        int getMaxQuantityPerUser(Long productId) {
            return maxQuantityPerUserMap.getOrDefault(productId, 1);
        }
    }

    private final QueueProperties queueProperties;
    private volatile ModeState state = ModeState.normal();

    public ModeManager(QueueProperties queueProperties) {
        this.queueProperties = queueProperties;
    }

    public ModeState getState() { return state; }

    // 편의 메서드 — state에 위임

    public boolean isHot() { return state.isHot(); }
    public boolean isDrain() { return state.isDrain(); }
    public boolean isHotProduct(Long productId) { return state.isHotProduct(productId); }
    public boolean isInGracePeriod() { return state.isInGracePeriod(); }
    public Set<Long> getHotProductIds() { return state.hotProductIds(); }
    public int getMaxQuantityPerUser(Long productId) { return state.getMaxQuantityPerUser(productId); }

    // 원자적 전환 — volatile write 1회

    public void switchToHot(Set<Long> productIds, Map<Long, Integer> maxQtyMap) {
        this.state = new ModeState(
                Mode.HOT, Set.copyOf(productIds), Map.copyOf(maxQtyMap),
                Instant.now().plusSeconds(queueProperties.getGracePeriodSeconds())
        );
    }

    public void switchToDrain() {
        ModeState cur = this.state;
        this.state = new ModeState(Mode.DRAIN, cur.hotProductIds(),
                cur.maxQuantityPerUserMap(), cur.graceDeadline());
    }

    public void switchToNormal() {
        this.state = ModeState.normal();
    }
}
