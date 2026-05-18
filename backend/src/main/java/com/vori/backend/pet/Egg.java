package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 가챠로 펫을 뽑기 위한 알. 사용자가 게임머니로 구매 → 개봉 시 gacha_pulls 추첨 → pets INSERT.
 * opened_at NULL = 인벤토리 (미개봉), 값 있음 = 이미 개봉됨.
 * 등급별 확률 분포는 구매 시점의 정책을 JSON 으로 박제 (정책 변경에도 과거 알 영향 X).
 */
@Entity
@Table(name = "eggs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Egg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "grade_name", nullable = false, length = 20)
    private String gradeName;

    @Column(name = "price_game_money", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer priceGameMoney;

    // 구매 시점의 등급별 등장 확률 JSON. 예: {"S":0.05,"A":0.15,"B":0.4,"C":0.4}
    @Column(name = "probability_distribution", nullable = false, columnDefinition = "JSON")
    private String probabilityDistribution;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;
}
