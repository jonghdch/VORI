package com.vori.backend.auth.dto;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;

public record AuthResponse(
    Long id,
    String email,
    String nickname,
    Role role
) {
    public static AuthResponse from(User user) {
        return new AuthResponse(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getRole()
        );
    }
}
