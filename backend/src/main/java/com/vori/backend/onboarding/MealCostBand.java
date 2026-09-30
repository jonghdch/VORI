package com.vori.backend.onboarding;

public enum MealCostBand {
    UNDER_7,
    MEAL_7_10,
    MEAL_10_15,
    OVER_15,
    UNKNOWN;

    public Integer midpoint() {
        return switch (this) {
            case UNDER_7 -> 6_000;
            case MEAL_7_10 -> 8_500;
            case MEAL_10_15 -> 12_500;
            case OVER_15 -> 18_000;
            case UNKNOWN -> null;
        };
    }

    public Integer initialStddev() {
        return switch (this) {
            case UNDER_7 -> 1_500;
            case MEAL_7_10 -> 2_000;
            case MEAL_10_15 -> 2_800;
            case OVER_15 -> 4_000;
            case UNKNOWN -> null;
        };
    }
}
