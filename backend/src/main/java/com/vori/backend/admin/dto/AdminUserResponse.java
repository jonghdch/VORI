package com.vori.backend.admin.dto;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;

import java.time.LocalDateTime;

/**
 * 어드민 유저 현황 테이블의 한 행.
 * passwordHash 등 민감 필드는 절대 노출하지 않는다 (User 엔티티에서 화이트리스트 매핑).
 */
public record AdminUserResponse(
        Long id,
        String email,
        String nickname,
        String name,
        Integer age,
        String job,
        Role role,
        Integer gameMoney,
        Integer totalSaved,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(),
                u.getEmail(),
                u.getNickname(),
                u.getName(),
                u.getAge(),
                u.getJob(),
                u.getRole(),
                u.getGameMoney(),
                u.getTotalSaved(),
                u.getCreatedAt()
        );
    }
}
