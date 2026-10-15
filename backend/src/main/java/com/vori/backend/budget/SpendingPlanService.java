package com.vori.backend.budget;

import com.vori.backend.common.StatType;
import com.vori.backend.budget.dto.*;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.YearMonth;
import java.util.*;

@Service @RequiredArgsConstructor
public class SpendingPlanService {
    private static final byte CURRENT_CALCULATION_VERSION = 2;
    private final UserStatBudgetRepository statBudgets;
    private final FixedExpenseRepository fixedExpenses;
    private final UserRepository users;

    /** 월 예산을 스탯 그룹별로 배분한 뒤, 고정비는 생활·고정비(지구력) 예산에서만 차감한다. */
    @Transactional public void ensurePlan(Long userId, String yearMonth) {
        User user = users.findById(userId).orElseThrow();
        List<UserStatBudget> existing = statBudgets.findByUserIdAndYearMonth(userId, yearMonth);
        // 월 예산 입력 전 생성된 0원 예산은 판정 보상을 전부 0으로 만든다. 월 예산이 등록된 뒤
        // 처음 확인할 때만 다시 만든다. 고정비 변경은 add/remove 에서 현재 월 계획을 재생성한다.
        if (!existing.isEmpty()) {
            boolean emptyPlan = existing.stream().mapToInt(UserStatBudget::getAmount).sum() == 0;
            boolean needsFixedCostRefresh = existing.stream()
                    .anyMatch(plan -> plan.getCalculationVersion() < CURRENT_CALCULATION_VERSION);
            if ((!emptyPlan && !needsFixedCostRefresh)
                    || Optional.ofNullable(user.getMonthlyBudget()).orElse(0) <= 0) return;
            statBudgets.deleteAll(existing);
            statBudgets.flush();
        }
        int monthlyBudget = Optional.ofNullable(user.getMonthlyBudget()).orElse(0);
        int fixed = fixedExpenses.findByUserIdOrderByIdAsc(userId).stream().mapToInt(FixedExpense::getAmount).sum();
        EnumMap<StatType, Integer> weights = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) weights.put(type, 25);
        // 복수 선택 가능. 선택한 분야마다 기본 비중보다 15포인트를 더 준다.
        user.budgetFocusStatTypes().forEach(type -> weights.merge(type, 15, Integer::sum));
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        int assigned = 0;
        for (StatType type : StatType.values()) {
            int amount = type == StatType.ENDURANCE
                    ? monthlyBudget - assigned
                    : monthlyBudget * weights.get(type) / totalWeight;
            // 고정비는 다른 스탯 그룹의 예산에는 영향을 주지 않고 생활·고정비 예산에서만 뺀다.
            if (type == StatType.ENDURANCE) amount = Math.max(0, amount - fixed);
            assigned += amount;
            statBudgets.save(UserStatBudget.builder()
                    .userId(userId).yearMonth(yearMonth).statType(type).amount(amount)
                    .calculationVersion(CURRENT_CALCULATION_VERSION).build());
        }
    }
    /** 월 예산·선호 분야·고정비가 바뀌면 현재 월 계획을 다시 배분한다. */
    @Transactional public void rebuildCurrentPlan(Long userId) {
        rebuildPlan(userId, YearMonth.now().toString());
    }
    @Transactional public void rebuildPlan(Long userId, String yearMonth) {
        List<UserStatBudget> existing = statBudgets.findByUserIdAndYearMonth(userId, yearMonth);
        if (!existing.isEmpty()) {
            statBudgets.deleteAll(existing);
            statBudgets.flush();
        }
        ensurePlan(userId, yearMonth);
    }
    @Transactional(readOnly = true) public StatBudgetResponse plan(Long userId, String yearMonth) {
        Map<StatType,Integer> values = new EnumMap<>(StatType.class);
        statBudgets.findByUserIdAndYearMonth(userId,yearMonth).forEach(b -> values.put(b.getStatType(), b.getAmount()));
        int fixed = fixedExpenses.findByUserIdOrderByIdAsc(userId).stream().mapToInt(FixedExpense::getAmount).sum();
        return new StatBudgetResponse(yearMonth, values, fixed, 0);
    }
    @Transactional public List<FixedExpenseResponse> listFixed(Long userId) { return fixedExpenses.findByUserIdOrderByIdAsc(userId).stream().map(FixedExpenseResponse::from).toList(); }
    @Transactional public FixedExpenseResponse addFixed(Long userId, FixedExpenseRequest req) {
        FixedExpense saved = fixedExpenses.save(FixedExpense.builder().userId(userId).name(req.name().trim()).amount(req.amount()).build());
        rebuildCurrentPlan(userId);
        return FixedExpenseResponse.from(saved);
    }
    @Transactional public void removeFixed(Long userId, Long id) {
        FixedExpense f=fixedExpenses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"고정비를 찾을 수 없어요."));
        if(!f.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"본인의 고정비만 삭제할 수 있어요.");
        fixedExpenses.delete(f);
        fixedExpenses.flush();
        rebuildCurrentPlan(userId);
    }
}
