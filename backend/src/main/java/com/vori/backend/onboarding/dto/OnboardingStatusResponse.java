package com.vori.backend.onboarding.dto;

import com.vori.backend.onboarding.MealCostBand;
import com.vori.backend.onboarding.MonthlyBudgetBand;
import com.vori.backend.onboarding.MonthlyGoal;
import com.vori.backend.onboarding.PrimarySpendArea;
import com.vori.backend.onboarding.SpendingHabit;
import com.vori.backend.onboarding.UserSpendingProfile;
import com.vori.backend.user.User;

public record OnboardingStatusResponse(
        boolean profileCompleted,
        boolean tutorialDone,
        Integer monthlyIncome,
        ProfileSummary profile
) {
    public static OnboardingStatusResponse of(User user, UserSpendingProfile profile) {
        return new OnboardingStatusResponse(
                profile != null,
                Boolean.TRUE.equals(user.getTutorialDone()),
                user.getMonthlyIncome(),
                profile == null ? null : ProfileSummary.from(profile)
        );
    }

    public record ProfileSummary(
            MonthlyBudgetBand monthlyBudgetBand,
            MealCostBand mealCostBand,
            PrimarySpendArea primarySpendArea,
            SpendingHabit spendingHabit,
            MonthlyGoal monthlyGoal,
            boolean baselineApplied
    ) {
        public static ProfileSummary from(UserSpendingProfile profile) {
            return new ProfileSummary(
                    profile.getMonthlyBudgetBand(),
                    profile.getMealCostBand(),
                    profile.getPrimarySpendArea(),
                    profile.getSpendingHabit(),
                    profile.getMonthlyGoal(),
                    Boolean.TRUE.equals(profile.getBaselineApplied())
            );
        }
    }
}
