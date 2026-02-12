package com.loopers.support.auth;

import com.loopers.domain.user.User;

public record AuthenticatedUser(Long id) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId());
    }
}
