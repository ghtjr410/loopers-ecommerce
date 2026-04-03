package com.loopers.interfaces.api.queue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.SessionService;
import com.loopers.application.queue.SessionService.SessionValidation;
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

/**
 * 블프 전체 API 세션 검증 필터.
 * 통과/차단 판단만 수행. 비즈니스 로직은 SessionService.validateAccess()에 위임.
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class SessionFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final ModeManager modeManager;
    private final QueueProperties queueProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!queueProperties.isSessionValidationEnabled()) return true;
        if (!modeManager.isEvent() && !modeManager.isDrain()) return true;

        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/v1/products") || uri.equals("/api/v1/orders"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long userId = extractUserId(request);
        if (userId == null) {
            reject(response, HttpStatus.FORBIDDEN, "대기열을 통해 진입해주세요");
            return;
        }

        SessionService.AccessType type = isOrderRequest(request)
                ? SessionService.AccessType.ORDER
                : SessionService.AccessType.QUERY;

        SessionValidation result = sessionService.validateAccess(userId, type);

        if (!result.isAllowed()) {
            reject(response, result.httpStatus(), result.message());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOrderRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "/api/v1/orders".equals(request.getRequestURI());
    }

    private Long extractUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(QueueAuthFilter.ATTR_QUEUE_USER_ID);
        if (attr instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(status.name(), message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
