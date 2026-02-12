package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        User user = userService.signUp(loginId, rawPassword, name, birthDate, email);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(Long id) {
        User user = userService.getById(id);
        return UserInfo.from(user);
    }

    public void changePassword(Long id, String newRawPassword) {
        userService.changePassword(id, newRawPassword);
    }
}
