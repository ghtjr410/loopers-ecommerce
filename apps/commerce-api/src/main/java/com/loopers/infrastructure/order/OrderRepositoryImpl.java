package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    // Command
    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    // Query
    @Override
    public Optional<Order> findByIdWithItems(Long id) {
        return orderJpaRepository.findByIdWithItems(id);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return orderJpaRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Override
    public Page<Order> findAllByUserIdAndStatusAndCreatedAtBetween(Long userId, OrderStatus status, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        return orderJpaRepository.findAllByUserIdAndStatusAndCreatedAtBetween(userId, status, startDate, endDate, pageable);
    }

    @Override
    public Page<Order> findAllByStatus(OrderStatus status, Pageable pageable) {
        return orderJpaRepository.findAllByStatus(status, pageable);
    }

    @Override
    public Page<Order> findAllByProductId(Long productId, Pageable pageable) {
        return orderJpaRepository.findAllByProductId(productId, pageable);
    }

    @Override
    public List<Order> findAllByStatusWithItems(OrderStatus status) {
        return orderJpaRepository.findAllByStatusWithItems(status);
    }

    @Override
    public int updateStatusIfCurrent(Long id, OrderStatus newStatus, OrderStatus currentStatus) {
        return orderJpaRepository.updateStatusIfCurrent(id, newStatus, currentStatus);
    }

    @Override
    public List<Order> findAllByStatusAndCreatedAtBeforeWithItems(OrderStatus status, ZonedDateTime threshold) {
        return orderJpaRepository.findAllByStatusAndCreatedAtBeforeWithItems(status, threshold);
    }

    @Override
    public int sumQuantityByUserIdAndProductId(Long userId, Long productId) {
        return orderJpaRepository.sumQuantityByUserIdAndProductId(userId, productId);
    }
}
