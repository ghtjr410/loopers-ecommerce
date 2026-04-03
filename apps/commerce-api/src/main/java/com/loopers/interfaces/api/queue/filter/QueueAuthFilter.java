package com.loopers.interfaces.api.queue.filter;

import com.loopers.application.user.UserService;
import com.loopers.domain.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 대기열/주문/상품 API에서 userId를 request attribute로 저장한다.
 * 후속 필터(RateLimitFilter, EarlyRejectionFilter, SessionFilter)가 attribute에서 userId를 읽는다.
 */
@Slf4j
@Component
@Order(0)
public class QueueAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_QUEUE_USER_ID = "queueUserId";

    private static final String HEADER_USER_ID = "X-Loopers-UserId";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserService userService;

    @Value("${auth.bypass.enabled:false}")
    private boolean bypassEnabled;

    public QueueAuthFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/v1/queue")
                || uri.equals("/api/v1/orders")
                || uri.startsWith("/api/v1/products"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long userId = resolveUserId(request);
        if (userId != null) {
            request.setAttribute(ATTR_QUEUE_USER_ID, userId);
        }
        filterChain.doFilter(request, response);
    }

    private Long resolveUserId(HttpServletRequest request) {
        if (bypassEnabled) {
            String userIdHeader = request.getHeader(HEADER_USER_ID);
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    return Long.parseLong(userIdHeader);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String password = request.getHeader(HEADER_LOGIN_PW);
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        try {
            User user = userService.authenticate(loginId, password);
            return user.getId();
        } catch (Exception e) {
            log.debug("대기열 인증 실패: loginId={}", loginId);
            return null;
        }
    }
}
