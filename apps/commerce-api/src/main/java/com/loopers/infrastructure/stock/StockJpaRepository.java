package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StockJpaRepository extends JpaRepository<Stock, Long> {

    // Command
    @Modifying
    @Query("UPDATE Stock s SET s.reservedQuantity = s.reservedQuantity + :amount " +
           "WHERE s.productId = :productId " +
           "AND s.quantity - s.reservedQuantity - s.confirmedQuantity >= :amount")
    int reserveIfAvailable(@Param("productId") Long productId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE Stock s SET s.reservedQuantity = s.reservedQuantity - :amount, " +
           "s.confirmedQuantity = s.confirmedQuantity + :amount " +
           "WHERE s.productId = :productId AND s.reservedQuantity >= :amount")
    int confirmIfReserved(@Param("productId") Long productId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE Stock s SET s.reservedQuantity = s.reservedQuantity - :amount " +
           "WHERE s.productId = :productId AND s.reservedQuantity >= :amount")
    int releaseReservedIfEnough(@Param("productId") Long productId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE Stock s SET s.confirmedQuantity = s.confirmedQuantity - :amount " +
           "WHERE s.productId = :productId AND s.confirmedQuantity >= :amount")
    int releaseConfirmedIfEnough(@Param("productId") Long productId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE Stock s SET s.quantity = :quantity " +
           "WHERE s.productId = :productId")
    int updateQuantity(@Param("productId") Long productId, @Param("quantity") int quantity);

    // Query
    Optional<Stock> findByProductId(Long productId);

    List<Stock> findAllByProductIdIn(Collection<Long> productIds);

    @Query("SELECT s.productId FROM Stock s WHERE s.reservedQuantity > 0")
    Set<Long> findProductIdsWithReservedStock();

    @Query("SELECT s.productId FROM Stock s WHERE s.quantity - s.reservedQuantity - s.confirmedQuantity <= 0")
    Set<Long> findSoldOutProductIds();
}
