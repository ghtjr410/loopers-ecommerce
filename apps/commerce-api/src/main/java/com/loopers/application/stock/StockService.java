package com.loopers.application.stock;

import com.loopers.domain.stock.Stock;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    // Command

    @Transactional
    public void createStock(Long productId, int quantity) {
        stockRepository.save(Stock.create(productId, quantity));
    }

    @Transactional
    public void updateQuantity(Long productId, int quantity) {
        Stock.validateQuantity(quantity);
        int updated = stockRepository.updateQuantity(productId, quantity);
        if (updated == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "재고 정보가 존재하지 않습니다");
        }
    }

    @Transactional
    public void reserve(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.reserveIfAvailable(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족하거나 존재하지 않는 상품입니다");
            }
        }
    }

    @Transactional
    public void confirm(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.confirmIfReserved(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "점유된 재고가 부족합니다");
            }
        }
    }

    @Transactional
    public void releaseReserved(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.releaseReservedIfEnough(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "해제할 점유 재고가 부족합니다");
            }
        }
    }

    @Transactional
    public void releaseConfirmed(Map<Long, Integer> productQuantities) {
        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            int updated = stockRepository.releaseConfirmedIfEnough(entry.getKey(), entry.getValue());
            if (updated == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "복원할 확정 재고가 부족합니다");
            }
        }
    }

    // Query

    @Transactional(readOnly = true)
    public Stock getStock(Long productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보가 존재하지 않습니다"));
    }

    @Transactional(readOnly = true)
    public Map<Long, Stock> getStocksMapByProductIds(Collection<Long> productIds) {
        return stockRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Stock::getProductId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Set<Long> findProductIdsWithReservedStock() {
        return stockRepository.findProductIdsWithReservedStock();
    }

    @Transactional(readOnly = true)
    public Set<Long> findSoldOutProductIds() {
        return stockRepository.findSoldOutProductIds();
    }
}
