package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    // Command
    Order save(Order order);

    // Query
    Optional<Order> findByIdWithItems(Long id);

    Page<Order> findAll(Pageable pageable);

    Page<Order> findAllByUserIdAndStatusAndCreatedAtBetween(Long userId, OrderStatus status, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);

    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findAllByProductId(Long productId, Pageable pageable);

    List<Order> findAllByStatusWithItems(OrderStatus status);

    int updateStatusIfCurrent(Long id, OrderStatus newStatus, OrderStatus currentStatus);

    List<Order> findAllByStatusAndCreatedAtBeforeWithItems(OrderStatus status, ZonedDateTime threshold);

    int sumQuantityByUserIdAndProductId(Long userId, Long productId);
}
