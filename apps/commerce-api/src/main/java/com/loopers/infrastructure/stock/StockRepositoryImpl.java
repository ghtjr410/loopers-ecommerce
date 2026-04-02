package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.Stock;
import com.loopers.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    // Command

    @Override
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }

    @Override
    public int reserveIfAvailable(Long productId, int amount) {
        return stockJpaRepository.reserveIfAvailable(productId, amount);
    }

    @Override
    public int confirmIfReserved(Long productId, int amount) {
        return stockJpaRepository.confirmIfReserved(productId, amount);
    }

    @Override
    public int releaseReservedIfEnough(Long productId, int amount) {
        return stockJpaRepository.releaseReservedIfEnough(productId, amount);
    }

    @Override
    public int releaseConfirmedIfEnough(Long productId, int amount) {
        return stockJpaRepository.releaseConfirmedIfEnough(productId, amount);
    }

    @Override
    public int updateQuantity(Long productId, int quantity) {
        return stockJpaRepository.updateQuantity(productId, quantity);
    }

    // Query

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId);
    }

    @Override
    public List<Stock> findAllByProductIdIn(Collection<Long> productIds) {
        return stockJpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public Set<Long> findProductIdsWithReservedStock() {
        return stockJpaRepository.findProductIdsWithReservedStock();
    }

    @Override
    public Set<Long> findSoldOutProductIds() {
        return stockJpaRepository.findSoldOutProductIds();
    }
}
