package com.loopers.interfaces.api.queue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.queue.ModeManager;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
@Order(2)
@RequiredArgsConstructor
public class EarlyRejectionFilter extends OncePerRequestFilter {

    private final ModeManager modeManager;
    private final QueueProperties props;
    private final ObjectMapper objectMapper;

    private volatile Set<Long> soldOutProducts = Set.of();
    private volatile Map<Long, Long> slotRemaining = Map.of();
    private volatile Map<Long, Long> queueSizes = Map.of();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && "/api/v1/queue/enter".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String productIdParam = request.getParameter("productId");
        if (productIdParam == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long productId;
        try {
            productId = Long.parseLong(productIdParam);
        } catch (NumberFormatException e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isSoldOut(productId)) {
            reject(response, HttpStatus.GONE, "SOLD_OUT", "해당 상품은 매진되었습니다");
            return;
        }

        if (isQueueFull(productId)) {
            response.setHeader("Retry-After", "10");
            reject(response, HttpStatus.SERVICE_UNAVAILABLE, "QUEUE_FULL", "대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSoldOut(Long productId) {
        if (modeManager.isHotProduct(productId)) {
            Long remaining = slotRemaining.get(productId);
            return remaining != null && remaining <= 0;
        }
        return soldOutProducts.contains(productId);
    }

    private boolean isQueueFull(Long productId) {
        Long size = queueSizes.get(productId);
        return size != null && size >= props.getMaxQueueSize();
    }

    public void updateSoldOutProducts(Set<Long> products) {
        this.soldOutProducts = products;
    }

    public void updateSlotRemaining(Map<Long, Long> remaining) {
        this.slotRemaining = remaining;
    }

    public void updateQueueSizes(Map<Long, Long> sizes) {
        this.queueSizes = sizes;
    }

    private void reject(HttpServletResponse response, HttpStatus status,
                        String errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(errorCode, message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
