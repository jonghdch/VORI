package com.vori.backend.budget;

import com.vori.backend.onboarding.UserSpendingProfileRepository;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * 예산 화면 GET 이 그 달 예산 행을 처음 만든다. StrictMode·탭 두 개로 같은 GET 이 동시에 오면
 * 둘 다 "없음" 을 보고 INSERT 해 uq_user_stat_budget 충돌이 났다 — 사용자 행을 잠가 차례로 만든다.
 */
class SpendingPlanEnsureTest {

    private final UserStatBudgetRepository budgets = mock(UserStatBudgetRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SpendingPlanService service = new SpendingPlanService(budgets, mock(FixedExpenseRepository.class),
            users, mock(UserSpendingProfileRepository.class));

    @Test
    @DisplayName("예산 생성 여부를 보기 전에 사용자 행을 잠근다")
    void locksUserBeforeCheckingExisting() {
        User user = User.builder().id(1L).monthlyIncome(3_000_000).build();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(budgets.findByUserIdAndYearMonth(1L, "2026-10")).thenReturn(List.of());

        service.ensurePlan(1L, "2026-10");

        var order = inOrder(users, budgets);
        order.verify(users).findByIdForUpdate(1L);
        order.verify(budgets).findByUserIdAndYearMonth(1L, "2026-10");
        verify(users, never()).findById(any());
    }
}
