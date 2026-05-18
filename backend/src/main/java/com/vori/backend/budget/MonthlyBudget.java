package com.vori.backend.budget;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월별 지출 예산. 사용자가 "이번 달 50만원 예산" 식으로 설정.
 * UNIQUE(user_id, year_month) — 사용자당 월별 1행.
 */
@Entity
@Table(name = "monthly_budgets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MonthlyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "year_month", nullable = false, columnDefinition = "CHAR(7)")
    private String yearMonth;

    @Column(nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer amount;
}
