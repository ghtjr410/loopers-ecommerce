package com.loopers.application.order;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.coupon.IssuedCouponSnapshot;
import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.product.ProductService;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.stock.StockService;
import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;
    private final IssuedCouponService issuedCouponService;
    private final PaymentFacade paymentFacade;
    private final ModeManager modeManager;

    // Command

    @Transactional
    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {
        validateHotProductOrder(command);

        // -- 1단계: 검증 + 계산 (읽기/순수 연산, 상태 변경 없음) --
        Map<Long, Integer> productQuantities = command.toQuantityMap();
        List<Product> products = productService.getActiveProducts(productQuantities.keySet());

        List<OrderCommand.CreateItem> orderItems = command.toCreateItems(products);
        BigDecimal totalAmount = OrderCommand.CreateItem.calculateTotalAmount(orderItems);

        IssuedCouponSnapshot couponSnapshot = command.issuedCouponId() != null
                ? issuedCouponService.createDiscountSnapshot(command.issuedCouponId(), userId, totalAmount)
                : IssuedCouponSnapshot.none();

        // -- 2단계: 상태 변경 (원자적) --
        stockService.reserve(productQuantities);

        if (couponSnapshot.isApplied()) {
            issuedCouponService.markUsedIfAvailable(command.issuedCouponId(), userId);
        }

        OrderCommand.CouponSnapshot orderCoupon = OrderCommand.CouponSnapshot.of(
                couponSnapshot.issuedCouponId(), couponSnapshot.discountAmount());

        Order order = orderService.createOrder(OrderCommand.Create.of(userId, orderItems, orderCoupon));

        return OrderInfo.from(order);
    }

    @Transactional
    public void expireOrder(Long orderId) {
        boolean expired = orderService.expireIfCreated(orderId);
        if (!expired) return;

        Order order = orderService.getOrder(orderId);
        stockService.releaseReserved(order.getProductQuantities());

        if (order.hasCoupon()) {
            issuedCouponService.restore(order.getIssuedCouponId());
        }
    }

    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        order.validateOwnership(userId);
        if (!order.isPaid()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다");
        }

        paymentFacade.cancelPayment(userId, orderId);
    }

    // Query

    @Transactional(readOnly = true)
    public OrderInfo getOrderDetail(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        order.validateOwnership(userId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderSummary> getOrderList(Long userId, OrderStatus status, ZonedDateTime startDateTime, ZonedDateTime endDateTime, Pageable pageable) {
        Page<Order> orders = orderService.findOrdersByUserIdAndStatusAndDateRange(userId, status, startDateTime, endDateTime, pageable);
        return orders.map(OrderInfo.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrderDetail(Long orderId) {
        Order order = orderService.getOrder(orderId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrderList(OrderStatus status, Pageable pageable) {
        Page<Order> orders = (status != null)
                ? orderService.findOrdersByStatus(status, pageable)
                : orderService.findAllOrders(pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrdersByProduct(Long productId, Pageable pageable) {
        Page<Order> orders = orderService.findOrdersByProductId(productId, pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }

    private void validateHotProductOrder(OrderCommand.Place command) {
        boolean hasHotProduct = command.items().stream()
                .anyMatch(item -> modeManager.isHotProduct(item.productId()));
        if (!hasHotProduct) return;

        if (command.items().size() > 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "한정 상품은 개별 주문해주세요");
        }

        OrderCommand.PlaceItem item = command.items().get(0);
        int maxQty = modeManager.getMaxQuantityPerUser(item.productId());
        if (item.quantity() > maxQty) {
            throw new CoreException(ErrorType.BAD_REQUEST, "인당 최대 " + maxQty + "개까지 주문 가능합니다");
        }
    }
}
