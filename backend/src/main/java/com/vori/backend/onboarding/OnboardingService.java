package com.vori.backend.onboarding;

import com.vori.backend.common.StatType;
import com.vori.backend.onboarding.dto.OnboardingStatusResponse;
import com.vori.backend.onboarding.dto.SpendingProfileRequest;
import com.vori.backend.onboarding.dto.SpendingProfileResponse;
import com.vori.backend.stats.UserStatStats;
import com.vori.backend.stats.UserStatStatsRepository;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final int INITIAL_PROFILE_SAMPLE_COUNT = 3;

    private final UserRepository userRepository;
    private final UserSpendingProfileRepository profileRepository;
    private final UserStatStatsRepository userStatStatsRepository;

    @Transactional(readOnly = true)
    public OnboardingStatusResponse status(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        UserSpendingProfile profile = profileRepository.findById(userId).orElse(null);
        return OnboardingStatusResponse.of(user, profile);
    }

    @Transactional
    public SpendingProfileResponse saveProfile(Long userId, SpendingProfileRequest req) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        LocalDateTime now = LocalDateTime.now();
        UserSpendingProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> UserSpendingProfile.builder()
                        .userId(userId)
                        .monthlyBudgetBand(req.monthlyBudgetBand())
                        .mealCostBand(req.mealCostBand())
                        .primarySpendArea(req.primarySpendArea())
                        .spendingHabit(req.spendingHabit())
                        .monthlyGoal(req.monthlyGoal())
                        .baselineApplied(false)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

        profile.update(
                req.monthlyBudgetBand(),
                req.mealCostBand(),
                req.primarySpendArea(),
                req.spendingHabit(),
                req.monthlyGoal(),
                now
        );

        if (!Boolean.TRUE.equals(profile.getBaselineApplied())) {
            applyMealBaseline(userId, req.mealCostBand(), profile);
        }

        UserSpendingProfile saved = profileRepository.save(profile);
        return SpendingProfileResponse.from(saved);
    }

    @Transactional
    public void complete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.markTutorialDone();
    }

    private void applyMealBaseline(Long userId, MealCostBand mealCostBand, UserSpendingProfile profile) {
        Integer midpoint = mealCostBand.midpoint();
        Integer stddev = mealCostBand.initialStddev();
        if (midpoint == null || stddev == null) return;

        UserStatStats stats = userStatStatsRepository
                .findByUserIdAndStatType(userId, StatType.ENERGY)
                .orElseThrow(() -> new IllegalStateException("user_stat_stats 초기화가 누락되었습니다."));

        if (stats.getSampleCount() >= 5) return;

        stats.updateEma(
                BigDecimal.valueOf(midpoint),
                BigDecimal.valueOf(stddev),
                Math.max(stats.getSampleCount(), INITIAL_PROFILE_SAMPLE_COUNT)
        );
        profile.markBaselineApplied();
    }
}
