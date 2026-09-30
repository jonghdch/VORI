package com.vori.backend.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_spending_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserSpendingProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "monthly_budget_band", nullable = false, length = 30)
    private MonthlyBudgetBand monthlyBudgetBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_cost_band", nullable = false, length = 30)
    private MealCostBand mealCostBand;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_spend_area", nullable = false, length = 30)
    private PrimarySpendArea primarySpendArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "spending_habit", nullable = false, length = 30)
    private SpendingHabit spendingHabit;

    @Enumerated(EnumType.STRING)
    @Column(name = "monthly_goal", nullable = false, length = 30)
    private MonthlyGoal monthlyGoal;

    @Column(name = "baseline_applied", nullable = false)
    @Builder.Default
    private Boolean baselineApplied = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void update(MonthlyBudgetBand monthlyBudgetBand,
                       MealCostBand mealCostBand,
                       PrimarySpendArea primarySpendArea,
                       SpendingHabit spendingHabit,
                       MonthlyGoal monthlyGoal,
                       LocalDateTime now) {
        this.monthlyBudgetBand = monthlyBudgetBand;
        this.mealCostBand = mealCostBand;
        this.primarySpendArea = primarySpendArea;
        this.spendingHabit = spendingHabit;
        this.monthlyGoal = monthlyGoal;
        this.updatedAt = now;
    }

    public void markBaselineApplied() {
        this.baselineApplied = true;
    }
}
