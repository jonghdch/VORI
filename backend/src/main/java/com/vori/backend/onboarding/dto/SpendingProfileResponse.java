package com.vori.backend.onboarding.dto;

import com.vori.backend.onboarding.UserSpendingProfile;

public record SpendingProfileResponse(
        boolean profileCompleted,
        boolean baselineApplied,
        String nextPath
) {
    public static SpendingProfileResponse from(UserSpendingProfile profile) {
        return new SpendingProfileResponse(true, Boolean.TRUE.equals(profile.getBaselineApplied()), "/onboarding");
    }
}
