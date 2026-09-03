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

    /**
     * 백틱 필수 — year_month 는 MySQL 예약어(INTERVAL ... YEAR_MONTH)다.
     * SELECT 는 Hibernate 가 별칭을 붙여 통과하지만 INSERT 의 컬럼 목록은 별칭 없이 나가
     * 문법 오류가 난다. 백틱을 빼면 예산 등록이 500 으로 실패한다. (Goal 도 같은 이유)
     */
    @Column(name = "`year_month`", nullable = false, columnDefinition = "CHAR(7)")
    private String yearMonth;

    @Column(nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer amount;

    public void updateAmount(int amount) {
        this.amount = amount;
    }
}
