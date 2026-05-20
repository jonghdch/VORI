package com.vori.backend.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원. 이메일·비번 해시·약관 동의 시각·튜토리얼 수집값·게임 자원 보유.
 * 회원가입 트랜잭션은 UserService.signup 참조. 가입 시 user_stat_stats 4행도 같이 INSERT.
 */
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

    // 현재 장착한 칭호. NULL = 미장착. FK 는 V1 마지막 ALTER 로 추가 (users↔user_titles 순환)
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

    // 선택 약관. NULL = 미동의, 시각 있음 = 동의
    @Column(name = "marketing_agreed_at")
    private LocalDateTime marketingAgreedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void addTotalSaved(int amount) {
        this.totalSaved = (this.totalSaved == null ? 0 : this.totalSaved) + amount;
    }
}
