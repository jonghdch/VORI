package com.vori.backend.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(length = 30)
    private String name;

    @Column(columnDefinition = "TINYINT UNSIGNED")
    private Integer age;

    @Column(length = 50)
    private String job;

    @Column(name = "monthly_income", columnDefinition = "INT UNSIGNED")
    private Integer monthlyIncome;

    @Column(name = "active_title_id")
    private Long activeTitleId;

    @Column(name = "total_saved")
    @Builder.Default
    private Integer totalSaved = 0;

    @Column(name = "game_money")
    @Builder.Default
    private Integer gameMoney = 0;

    @Column(name = "tutorial_done")
    @Builder.Default
    private Boolean tutorialDone = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('USER','ADMIN')")
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "terms_agreed_at", nullable = false)
    private LocalDateTime termsAgreedAt;

    @Column(name = "privacy_agreed_at", nullable = false)
    private LocalDateTime privacyAgreedAt;

    @Column(name = "marketing_agreed_at")
    private LocalDateTime marketingAgreedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
