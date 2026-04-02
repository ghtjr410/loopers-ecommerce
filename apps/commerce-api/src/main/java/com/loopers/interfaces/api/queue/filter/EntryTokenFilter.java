package com.loopers.interfaces.api.queue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(3)
@RequiredArgsConstructor
public class EntryTokenFilter extends OncePerRequestFilter {

    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";
    private static final String HEADER_USER_ID = "X-Loopers-UserId";
    private static final String ATTR_ENTRY_PRODUCT_ID = "entryProductId";

    private final QueueService queueService;
    private final ModeManager modeManager;
    private final QueueProperties queueProperties;
    private final ObjectMapper objectMapper;

    @Value("${auth.bypass.enabled:false}")
    private boolean bypassEnabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!queueProperties.isTokenValidationEnabled()) return true;
        return !("POST".equals(request.getMethod())
                && "/api/v1/orders".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (modeManager.isInGracePeriod()) {
            filterChain.doFilter(request, response);
            return;
        }

        String tokenHeader = request.getHeader(ENTRY_TOKEN_HEADER);
        if (tokenHeader == null || tokenHeader.isBlank()) {
            reject(response, HttpStatus.FORBIDDEN, "대기열을 통해 진입해주세요");
            return;
        }

        String[] parts = tokenHeader.split(":");
        if (parts.length != 2) {
            reject(response, HttpStatus.BAD_REQUEST, "토큰 형식이 올바르지 않습니다");
            return;
        }

        String token = parts[0];
        Long productId;
        try {
            productId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            reject(response, HttpStatus.BAD_REQUEST, "토큰 형식이 올바르지 않습니다");
            return;
        }

        Long userId = extractUserId(request);
        if (userId == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "인증 정보가 필요합니다");
            return;
        }

        if (!queueService.validateToken(userId, productId, token)) {
            reject(response, HttpStatus.FORBIDDEN, "토큰이 만료되었습니다");
            return;
        }

        queueService.extendTokenTtl(userId, productId);
        request.setAttribute(ATTR_ENTRY_PRODUCT_ID, productId);
        filterChain.doFilter(request, response);
    }

    private Long extractUserId(HttpServletRequest request) {
        if (!bypassEnabled) {
            return null;
        }
        String userIdHeader = request.getHeader(HEADER_USER_ID);
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(status.name(), message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
