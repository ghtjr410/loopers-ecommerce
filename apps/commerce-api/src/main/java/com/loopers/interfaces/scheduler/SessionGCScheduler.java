package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.QueueFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료 세션 정리 스케줄러.
 * 10초마다 QueueFacade.cleanExpiredSessions()에 위임.
 */
@Component
@RequiredArgsConstructor
public class SessionGCScheduler {

    private final QueueFacade queueFacade;

    @Scheduled(fixedDelayString = "${queue.gc-interval-ms:10000}")
    public void cleanExpiredSessions() {
        queueFacade.cleanExpiredSessions();
    }
}
