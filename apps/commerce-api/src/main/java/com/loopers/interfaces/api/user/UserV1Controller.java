package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import com.loopers.support.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller implements UserApiV1Spec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signUp(@Valid @RequestBody UserV1Dto.SignUpRequest request) {
        UserInfo info = userFacade.signUp(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(@AuthUser AuthenticatedUser authUser) {
        UserInfo info = userFacade.getMyInfo(authUser.id());
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<Void> changePassword(
            @AuthUser AuthenticatedUser authUser,
            @Valid @RequestBody UserV1Dto.ChangePasswordRequest request) {
        userFacade.changePassword(authUser.id(), request.newPassword());
        return ApiResponse.success();
    }
}
