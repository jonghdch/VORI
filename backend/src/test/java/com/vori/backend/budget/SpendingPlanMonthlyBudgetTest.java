package com.vori.backend.budget;

import com.vori.backend.common.StatType;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 월 예산을 스탯 그룹에 나누고, 고정비는 생활·고정비(지구력) 예산에서만 뺀다. */
class SpendingPlanMonthlyBudgetTest {

    private final UserStatBudgetRepository budgets = mock(UserStatBudgetRepository.class);
    private final FixedExpenseRepository fixed = mock(FixedExpenseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SpendingPlanService service = new SpendingPlanService(budgets, fixed, users);

    private Map<StatType, Integer> plan(User user, List<FixedExpense> fixedRows) {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(budgets.findByUserIdAndYearMonth(1L, "2026-10")).thenReturn(List.of());
        when(fixed.findByUserIdOrderByIdAsc(1L)).thenReturn(fixedRows);
        service.ensurePlan(1L, "2026-10");
        ArgumentCaptor<UserStatBudget> saved = ArgumentCaptor.forClass(UserStatBudget.class);
        verify(budgets, times(4)).save(saved.capture());
        Map<StatType, Integer> amounts = new EnumMap<>(StatType.class);
        saved.getAllValues().forEach(b -> amounts.put(b.getStatType(), b.getAmount()));
        return amounts;
    }

    private static FixedExpense fixedOf(int amount) {
        return FixedExpense.builder().userId(1L).name("통신비").amount(amount).build();
    }

    @Test
    @DisplayName("고정비는 지구력 예산에서만 빠지고, 고정비가 없으면 그만큼 돌아온다")
    void fixedCostComesOutOfEnduranceOnly() {
        User user = User.builder().id(1L).monthlyBudget(1_000_000).build();

        Map<StatType, Integer> withFixed = plan(user, List.of(fixedOf(100_000)));

        assertThat(withFixed.get(StatType.ENERGY)).isEqualTo(250_000);
        assertThat(withFixed.get(StatType.CHARM)).isEqualTo(250_000);
        assertThat(withFixed.get(StatType.IQ)).isEqualTo(250_000);
        assertThat(withFixed.get(StatType.ENDURANCE)).isEqualTo(150_000);

        clearInvocations(budgets);
        assertThat(plan(user, List.of()).get(StatType.ENDURANCE)).isEqualTo(250_000);
    }

    @Test
    @DisplayName("체크한 분야는 다른 분야보다 예산을 더 받는다")
    void focusedGroupGetsMore() {
        User user = User.builder().id(1L).monthlyBudget(1_150_000).budgetFocusStats("ENERGY").build();

        Map<StatType, Integer> amounts = plan(user, List.of());

        assertThat(amounts.get(StatType.ENERGY)).isEqualTo(400_000);
        assertThat(amounts.get(StatType.CHARM)).isEqualTo(250_000);
        assertThat(amounts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1_150_000);
    }

    @Test
    @DisplayName("월 예산을 안 정했으면 월 수입에서 예비비 15%를 뺀 금액을 쓴다")
    void withoutMonthlyBudgetFallsBackToIncome() {
        User user = User.builder().id(1L).monthlyIncome(1_000_000).build();

        Map<StatType, Integer> amounts = plan(user, List.of());

        assertThat(amounts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(850_000);
    }

    @Test
    @DisplayName("예전 계산 버전의 지난 달 예산은 지금 설정으로 다시 만들지 않는다")
    void pastMonthPlanIsNotRegenerated() {
        String lastMonth = java.time.YearMonth.now().minusMonths(1).toString();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).monthlyBudget(1_000_000).build()));
        when(budgets.findByUserIdAndYearMonth(1L, lastMonth)).thenReturn(List.of(
                UserStatBudget.builder().userId(1L).yearMonth(lastMonth).statType(StatType.ENERGY).amount(500_000).calculationVersion((byte) 1).build()));

        service.ensurePlan(1L, lastMonth);

        verify(budgets, never()).deleteAll(any());
        verify(budgets, never()).save(any());
    }

    @Test
    @DisplayName("예전 계산 버전의 이번 달 예산은 새 규칙으로 다시 만든다")
    void currentMonthPlanIsRegenerated() {
        String thisMonth = java.time.YearMonth.now().toString();
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).monthlyBudget(1_000_000).build()));
        when(budgets.findByUserIdAndYearMonth(1L, thisMonth)).thenReturn(List.of(
                UserStatBudget.builder().userId(1L).yearMonth(thisMonth).statType(StatType.ENERGY).amount(500_000).calculationVersion((byte) 1).build()));
        when(fixed.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        service.ensurePlan(1L, thisMonth);

        verify(budgets).deleteAll(any());
        verify(budgets, times(4)).save(any());
    }

    @Test
    @DisplayName("다시 만들 때 기존 예산을 읽기 전에 사용자 행을 잠근다")
    void rebuildLocksUserBeforeReadingRows() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).monthlyBudget(1_000_000).build()));
        when(budgets.findByUserIdAndYearMonth(1L, "2026-10")).thenReturn(List.of());
        when(fixed.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        service.rebuildPlan(1L, "2026-10");

        var order = inOrder(users, budgets);
        order.verify(users).findByIdForUpdate(1L);
        order.verify(budgets).findByUserIdAndYearMonth(1L, "2026-10");
    }

    @Test
    @DisplayName("월 예산과 월 수입이 둘 다 없으면 0원이다")
    void withoutBudgetAndIncomeIsZero() {
        Map<StatType, Integer> amounts = plan(User.builder().id(1L).build(), List.of());

        assertThat(amounts.values()).containsOnly(0);
    }
}
