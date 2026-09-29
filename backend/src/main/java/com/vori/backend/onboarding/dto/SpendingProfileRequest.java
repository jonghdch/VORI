package com.vori.backend.onboarding.dto;

import com.vori.backend.onboarding.MealCostBand;
import com.vori.backend.onboarding.MonthlyBudgetBand;
import com.vori.backend.onboarding.MonthlyGoal;
import com.vori.backend.onboarding.PrimarySpendArea;
import com.vori.backend.onboarding.SpendingHabit;
import jakarta.validation.constraints.NotNull;

public record SpendingProfileRequest(
        @NotNull MonthlyBudgetBand monthlyBudgetBand,
        @NotNull MealCostBand mealCostBand,
        @NotNull PrimarySpendArea primarySpendArea,
        @NotNull SpendingHabit spendingHabit,
        @NotNull MonthlyGoal monthlyGoal
) {
}
