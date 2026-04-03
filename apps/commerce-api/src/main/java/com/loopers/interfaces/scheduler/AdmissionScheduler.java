package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.QueueFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 고정 rate 입장 스케줄러.
 * 10초마다 QueueFacade.admitBatch()에 위임.
 */
@Component
@RequiredArgsConstructor
public class AdmissionScheduler {

    private final QueueFacade queueFacade;

    @Scheduled(fixedDelayString = "${queue.admission-interval-ms:10000}")
    public void admit() {
        queueFacade.admitBatch();
    }
}
