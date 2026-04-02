package com.loopers.interfaces.api.queue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record QueueRequest() {

    // Command

    public record ModeChange(
            @NotNull Mode mode,
            List<@Valid HotProduct> products
    ) {
        public enum Mode { HOT, DRAIN, NORMAL }

        public Set<Long> toProductIds() {
            if (products == null) return Set.of();
            return products.stream()
                    .map(HotProduct::productId)
                    .collect(Collectors.toSet());
        }

        public Map<Long, Integer> toMaxQuantityPerUserMap() {
            if (products == null) return Map.of();
            return products.stream()
                    .collect(Collectors.toMap(
                            HotProduct::productId,
                            HotProduct::maxQuantityPerUser
                    ));
        }
    }

    public record HotProduct(
            @NotNull Long productId,
            @NotNull @Positive Integer maxQuantityPerUser
    ) {}
}
