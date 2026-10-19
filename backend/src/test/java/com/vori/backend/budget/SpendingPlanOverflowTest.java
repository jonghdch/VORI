package com.vori.backend.budget;

import com.vori.backend.onboarding.UserSpendingProfileRepository;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import com.vori.backend.user.dto.ProfileUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 큰 월 수입에서도 스탯 예산 배분이 int 범위를 넘지 않는다. */
class SpendingPlanOverflowTest {

    private final UserStatBudgetRepository budgets = mock(UserStatBudgetRepository.class);
    private final FixedExpenseRepository fixed = mock(FixedExpenseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserSpendingProfileRepository profiles = mock(UserSpendingProfileRepository.class);
    private final SpendingPlanService service = new SpendingPlanService(budgets, fixed, users, profiles);

    @Test
    @DisplayName("월 수입 1억이면 가용액 8,500만 원이 음수 없이 4개 스탯에 모두 배분된다")
    void largeIncomeIsSplitWithoutOverflow() {
        User user = User.builder().id(1L).monthlyIncome(100_000_000).build();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(budgets.findByUserIdAndYearMonth(1L, "2026-10")).thenReturn(List.of());
        when(fixed.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(profiles.findById(1L)).thenReturn(Optional.empty());

        service.ensurePlan(1L, "2026-10");

        ArgumentCaptor<UserStatBudget> saved = ArgumentCaptor.forClass(UserStatBudget.class);
        verify(budgets, times(4)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(b -> assertThat(b.getAmount()).isNotNegative());
        assertThat(saved.getAllValues().stream().mapToInt(UserStatBudget::getAmount).sum()).isEqualTo(85_000_000);
    }

    @Test
    @DisplayName("프로필 수정의 월 수입도 온보딩과 같은 1억 상한을 둔다")
    void profileIncomeHasUpperBound() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new ProfileUpdateRequest("닉네임", "홍길동", 20, null, 100_000_000))).isEmpty();
        assertThat(validator.validate(new ProfileUpdateRequest("닉네임", "홍길동", 20, null, 100_000_001))).isNotEmpty();
    }
}
