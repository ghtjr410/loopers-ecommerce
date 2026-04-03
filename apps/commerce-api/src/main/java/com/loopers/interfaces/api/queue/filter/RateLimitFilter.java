package com.loopers.interfaces.api.queue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.queue.config.QueueProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String SLIDING_WINDOW_LUA =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1]) " +
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]) " +
            "local count = redis.call('ZCARD', KEYS[1]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
            "return count";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final QueueProperties props;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicInteger> ipCounters = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            QueueProperties props,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedRate = 1000)
    public void clearIpCounters() {
        ipCounters.clear();
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
        String ip = request.getRemoteAddr();
        if (isIpRateLimited(ip)) {
            meterRegistry.counter("rate.limited.ip.total").increment();
            response.setHeader("Retry-After", "1");
            reject(response, "IP 요청 한도를 초과했습니다");
            return;
        }

        Long userId = extractUserId(request);
        if (userId != null && isUserRateLimited(userId, request)) {
            meterRegistry.counter("rate.limited.user.total").increment();
            response.setHeader("Retry-After", "10");
            reject(response, "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isIpRateLimited(String ip) {
        AtomicInteger counter = ipCounters.computeIfAbsent(ip, k -> new AtomicInteger(0));
        return counter.incrementAndGet() > props.getIpRateLimitPerSecond();
    }

    private boolean isUserRateLimited(Long userId, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        String api;
        int limit;
        int windowSeconds;

        if ("POST".equals(method) && "/api/v1/queue/enter".equals(uri)) {
            api = "queue-enter";
            limit = props.getQueueEnterLimitPerWindow();
            windowSeconds = props.getQueueEnterWindowSeconds();
        } else if ("GET".equals(method) && "/api/v1/queue/position".equals(uri)) {
            api = "queue-position";
            limit = props.getPositionLimitPerSecond();
            windowSeconds = 1;
        } else if ("POST".equals(method) && "/api/v1/orders".equals(uri)) {
            api = "orders";
            limit = props.getOrderLimitPerMinute();
            windowSeconds = 60;
        } else {
            return false;
        }

        try {
            String key = "rate:user:" + userId + ":" + api;
            double now = System.currentTimeMillis() / 1000.0;
            double windowStart = now - windowSeconds;

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, Long.class);
            Long count = masterRedisTemplate.execute(script,
                    Collections.singletonList(key),
                    String.valueOf(windowStart),
                    String.valueOf(now),
                    UUID.randomUUID().toString(),
                    String.valueOf(windowSeconds));

            return count != null && count > limit;
        } catch (Exception e) {
            log.warn("Redis Rate Limit 실패, skip", e);
            return false;
        }
    }

    private Long extractUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(QueueAuthFilter.ATTR_QUEUE_USER_ID);
        if (attr instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail("RATE_LIMITED", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
