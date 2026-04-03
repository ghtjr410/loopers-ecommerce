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

import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;

@Component
@Order(2)
@RequiredArgsConstructor
public class EarlyRejectionFilter extends OncePerRequestFilter {

    private final ModeManager modeManager;
    private final QueueProperties props;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private volatile long currentQueueSize = 0;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && "/api/v1/queue/enter".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // DRAIN 모드: 새 진입 거부
        if (modeManager.isDrain()) {
            meterRegistry.counter("early.rejection.total", "reason", "DRAIN").increment();
            reject(response, HttpStatus.SERVICE_UNAVAILABLE, "DRAIN", "대기열이 마감되었습니다");
            return;
        }

        // 큐 만석 확인
        if (currentQueueSize >= props.getMaxQueueSize()) {
            meterRegistry.counter("early.rejection.total", "reason", "QUEUE_FULL").increment();
            response.setHeader("Retry-After", "10");
            reject(response, HttpStatus.SERVICE_UNAVAILABLE, "QUEUE_FULL", "대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요");
            return;
        }

        filterChain.doFilter(request, response);
    }

    public void updateQueueSize(long size) {
        this.currentQueueSize = size;
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
