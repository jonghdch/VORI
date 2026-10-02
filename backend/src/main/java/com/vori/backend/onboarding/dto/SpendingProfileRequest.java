package com.vori.backend.onboarding.dto;

import com.vori.backend.onboarding.MealCostBand;
import com.vori.backend.onboarding.MonthlyBudgetBand;
import com.vori.backend.onboarding.MonthlyGoal;
import com.vori.backend.onboarding.PrimarySpendArea;
import com.vori.backend.onboarding.SpendingHabit;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SpendingProfileRequest(
        /** 월 수입(원). 필수 — 식비 외 세 타입의 초기 판정 기준선을 여기서 유도한다(BaselineSeeder). */
        @NotNull(message = "월 수입을 입력해 주세요")
        @Min(value = 0, message = "월 수입은 0 이상으로 입력해 주세요")
        @Max(value = 100_000_000, message = "월 수입이 너무 큽니다")
        Integer monthlyIncome,
        @NotNull MonthlyBudgetBand monthlyBudgetBand,
        @NotNull MealCostBand mealCostBand,
        @NotNull PrimarySpendArea primarySpendArea,
        @NotNull SpendingHabit spendingHabit,
        @NotNull MonthlyGoal monthlyGoal
) {
}
