package com.vori.backend.onboarding;

import com.vori.backend.onboarding.dto.OnboardingStatusResponse;
import com.vori.backend.onboarding.dto.SpendingProfileRequest;
import com.vori.backend.onboarding.dto.SpendingProfileResponse;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import com.vori.backend.budget.SpendingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final UserSpendingProfileRepository profileRepository;
    private final BaselineSeeder baselineSeeder;
    private final SpendingPlanService spendingPlanService;

    @Transactional(readOnly = true)
    public OnboardingStatusResponse status(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        UserSpendingProfile profile = profileRepository.findById(userId).orElse(null);
        return OnboardingStatusResponse.of(user, profile);
    }

    @Transactional
    public SpendingProfileResponse saveProfile(Long userId, SpendingProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        // 월 수입은 프로필 설정과 같은 컬럼(users.monthly_income)에 둔다 — 거기서 나중에 고칠 수 있게.
        user.updateMonthlyIncome(req.monthlyIncome());

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

        // 초기 판정 기준선 — 식비는 한 끼 식비 답, 나머지 세 타입은 월 수입에서 유도한다.
        // 실제 지출이 아직 없는 타입만 채우므로 설문을 다시 해도 쌓인 기록은 지워지지 않는다.
        if (baselineSeeder.seed(userId, req.monthlyIncome(), req.mealCostBand())) {
            profile.markBaselineApplied();
        }
        UserSpendingProfile saved = profileRepository.save(profile);
        // 설문 응답(수입·주 소비 영역)을 바탕으로 4개 스탯 예산을 한 번 생성한다.
        spendingPlanService.ensurePlan(userId, java.time.YearMonth.now().toString());
        return SpendingProfileResponse.from(saved);
    }

    @Transactional
    public void complete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        user.markTutorialDone();
    }
}
