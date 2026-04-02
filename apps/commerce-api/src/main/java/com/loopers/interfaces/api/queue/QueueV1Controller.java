package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueService;
import com.loopers.application.queue.dto.QueueEntryResponse;
import com.loopers.application.queue.dto.QueuePositionResponse;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueV1Controller {

    private final QueueService queueService;

    // Command

    @PostMapping("/enter")
    public ApiResponse<QueueEntryResponse> enter(
            @AuthUser AuthenticatedUser user,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity
    ) {
        QueueEntryResponse response = queueService.enter(user.id(), productId, quantity);
        return ApiResponse.success(response);
    }

    // Query

    @GetMapping("/position")
    public ApiResponse<QueuePositionResponse> getPosition(
            @AuthUser AuthenticatedUser user,
            @RequestParam Long productId
    ) {
        QueuePositionResponse response = queueService.getPosition(user.id(), productId);
        return ApiResponse.success(response);
    }
}
