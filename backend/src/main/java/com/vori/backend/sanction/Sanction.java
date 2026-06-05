package com.vori.backend.sanction;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저 제재 1건. type=SUSPENSION 이면 expires_at 로 기간 지정.
 * lifted_at 이 차면 조기 해제됨. 상태(활성/만료/해제)는 응답 시 파생 계산.
 */
@Entity
@Table(name = "sanctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Sanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('WARNING','SUSPENSION','BAN')")
    private SanctionType type;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    public void lift(LocalDateTime when) {
        this.liftedAt = when;
    }
}
