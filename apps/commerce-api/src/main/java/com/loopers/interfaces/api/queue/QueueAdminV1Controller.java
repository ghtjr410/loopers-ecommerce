package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.CapacityService;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.stock.StockService;
import com.loopers.domain.stock.Stock;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api-admin/v1/queue")
@RequiredArgsConstructor
public class QueueAdminV1Controller {

    private final ModeManager modeManager;
    private final CapacityService capacityService;
    private final StockService stockService;

    // Command

    @PostMapping("/mode")
    public ApiResponse<Void> changeMode(@Valid @RequestBody QueueRequest.ModeChange request) {
        switch (request.mode()) {
            case HOT -> activateHotMode(request);
            case DRAIN -> modeManager.switchToDrain();
            case NORMAL -> deactivateHotMode();
        }
        return ApiResponse.success();
    }

    private void activateHotMode(QueueRequest.ModeChange request) {
        Set<Long> productIds = request.toProductIds();
        Map<Long, Integer> maxQuantityPerUserMap = request.toMaxQuantityPerUserMap();

        for (Long productId : productIds) {
            Stock stock = stockService.getStock(productId);
            int available = stock.getAvailableQuantity();
            int maxQty = maxQuantityPerUserMap.get(productId);
            capacityService.initializeCapacity(productId, available, maxQty);
        }

        modeManager.switchToHot(productIds, maxQuantityPerUserMap);
    }

    private void deactivateHotMode() {
        Set<Long> previousHotProducts = modeManager.getHotProductIds();
        modeManager.switchToNormal();
        previousHotProducts.forEach(capacityService::clearCapacity);
    }
}
