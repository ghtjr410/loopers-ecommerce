package com.loopers.interfaces.api.queue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.SessionService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 블프 전체 API 세션 검증 필터.
 *
 * NORMAL 모드: shouldNotFilter → bypass (블프 전용 서버이므로 방어용)
 * EVENT/DRAIN 모드: 세션 검증 실행
 * Grace Period (DRAIN 전환 후 60초): 세션 검증 시도 → Redis 실패 시에만 skip (소프트 바이패스)
 *
 * PLP/PDP 조회: ACTIVE 또는 CONSUMED 시 통과.
 * 주문 (POST /orders): ACTIVE 시에만 통과.
 */
@Slf4j
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
        // NORMAL 모드: 블프 이벤트 아님 → bypass
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

        // 세션 조회 — Redis 실패 시 Grace Period이면 소프트 바이패스
        SessionService.SessionInfo session;
        try {
            session = sessionService.getSession(userId);
        } catch (Exception e) {
            if (modeManager.isInGracePeriod()) {
                log.warn("Grace Period 소프트 바이패스: userId={}, Redis 실패", userId, e);
                filterChain.doFilter(request, response);
                return;
            }
            log.error("세션 조회 실패: userId={}", userId, e);
            reject(response, HttpStatus.INTERNAL_SERVER_ERROR, "일시적 오류가 발생했습니다");
            return;
        }

        if (session == null) {
            reject(response, HttpStatus.FORBIDDEN, "대기열을 통해 진입해주세요");
            return;
        }

        boolean isOrderRequest = "POST".equals(request.getMethod())
                && "/api/v1/orders".equals(request.getRequestURI());

        if (isOrderRequest) {
            if (!SessionService.STATUS_ACTIVE.equals(session.status())) {
                reject(response, HttpStatus.FORBIDDEN, "주문 가능한 세션 상태가 아닙니다");
                return;
            }
        }
        // PLP/PDP는 ACTIVE 또는 CONSUMED 모두 통과

        // Activity TTL 연장
        try {
            sessionService.extendTtl(userId);
        } catch (Exception e) {
            log.warn("세션 TTL 연장 실패: userId={}", userId, e);
        }

        filterChain.doFilter(request, response);
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
