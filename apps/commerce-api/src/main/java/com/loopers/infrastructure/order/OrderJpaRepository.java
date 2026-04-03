package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    // Query
    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query(value = "SELECT o FROM Order o ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM Order o")
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "SELECT o FROM Order o WHERE o.userId = :userId "
                 + "AND (:status IS NULL OR o.status = :status) "
                 + "AND (:startDate IS NULL OR o.createdAt >= :startDate) "
                 + "AND (:endDate IS NULL OR o.createdAt < :endDate) "
                 + "ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.userId = :userId "
                      + "AND (:status IS NULL OR o.status = :status) "
                      + "AND (:startDate IS NULL OR o.createdAt >= :startDate) "
                      + "AND (:endDate IS NULL OR o.createdAt < :endDate)")
    Page<Order> findAllByUserIdAndStatusAndCreatedAtBetween(@Param("userId") Long userId,
                                                            @Param("status") OrderStatus status,
                                                            @Param("startDate") ZonedDateTime startDate,
                                                            @Param("endDate") ZonedDateTime endDate,
                                                            Pageable pageable);

    @Query(value = "SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Page<Order> findAllByStatus(@Param("status") OrderStatus status, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o JOIN o.orderItems oi "
                 + "WHERE oi.productId = :productId ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o JOIN o.orderItems oi "
                      + "WHERE oi.productId = :productId")
    Page<Order> findAllByProductId(@Param("productId") Long productId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.status = :status")
    List<Order> findAllByStatusWithItems(@Param("status") OrderStatus status);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus WHERE o.id = :id AND o.status = :currentStatus")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("newStatus") OrderStatus newStatus,
                              @Param("currentStatus") OrderStatus currentStatus);

    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems "
         + "WHERE o.status = :status AND o.createdAt < :threshold")
    List<Order> findAllByStatusAndCreatedAtBeforeWithItems(@Param("status") OrderStatus status,
                                                           @Param("threshold") ZonedDateTime threshold);

    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi "
         + "JOIN oi.order o "
         + "WHERE o.userId = :userId AND oi.productId = :productId "
         + "AND o.status <> com.loopers.domain.order.OrderStatus.CANCELED")
    int sumQuantityByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
