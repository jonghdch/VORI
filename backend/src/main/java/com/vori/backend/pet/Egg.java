package com.vori.backend.pet;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "probability_distribution", nullable = false, columnDefinition = "JSON")
    private String probabilityDistribution;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;
}
