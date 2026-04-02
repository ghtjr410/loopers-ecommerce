package com.loopers.application.queue;

import com.loopers.interfaces.api.queue.config.QueueProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class ModeManager {

    public enum Mode { NORMAL, HOT, DRAIN }

    private final QueueProperties queueProperties;

    private volatile Mode currentMode = Mode.NORMAL;
    private volatile Set<Long> hotProductIds = Set.of();
    private volatile Map<Long, Integer> maxQuantityPerUserMap = Map.of();
    private volatile Instant graceDeadline = Instant.MIN;

    public ModeManager(QueueProperties queueProperties) {
        this.queueProperties = queueProperties;
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    public boolean isHot() {
        return currentMode == Mode.HOT;
    }

    public boolean isDrain() {
        return currentMode == Mode.DRAIN;
    }

    public boolean isHotProduct(Long productId) {
        return hotProductIds.contains(productId);
    }

    public boolean isInGracePeriod() {
        return Instant.now().isBefore(graceDeadline);
    }

    public Set<Long> getHotProductIds() {
        return hotProductIds;
    }

    public int getMaxQuantityPerUser(Long productId) {
        return maxQuantityPerUserMap.getOrDefault(productId, Integer.MAX_VALUE);
    }

    public void switchToHot(Set<Long> productIds, Map<Long, Integer> maxQuantityPerUser) {
        this.hotProductIds = Set.copyOf(productIds);
        this.maxQuantityPerUserMap = Map.copyOf(maxQuantityPerUser);
        this.graceDeadline = Instant.now().plusSeconds(queueProperties.getGracePeriodSeconds());
        this.currentMode = Mode.HOT;
    }

    public void switchToDrain() {
        this.currentMode = Mode.DRAIN;
    }

    public void switchToNormal() {
        this.currentMode = Mode.NORMAL;
        this.hotProductIds = Set.of();
        this.maxQuantityPerUserMap = Map.of();
    }
}
