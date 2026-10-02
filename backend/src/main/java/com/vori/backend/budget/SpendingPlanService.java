package com.vori.backend.budget;

import com.vori.backend.common.StatType;
import com.vori.backend.onboarding.PrimarySpendArea;
import com.vori.backend.onboarding.UserSpendingProfile;
import com.vori.backend.onboarding.UserSpendingProfileRepository;
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
    private final UserStatBudgetRepository statBudgets;
    private final FixedExpenseRepository fixedExpenses;
    private final UserRepository users;
    private final UserSpendingProfileRepository profiles;

    /** 설문으로 만든 이번 달 예산은 이후 고정비를 추가해도 바꾸지 않는다. */
    @Transactional public void ensurePlan(Long userId, String yearMonth) {
        User user = users.findById(userId).orElseThrow();
        List<UserStatBudget> existing = statBudgets.findByUserIdAndYearMonth(userId, yearMonth);
        // 수입 입력 전 생성된 0원 예산은 판정 보상을 전부 0으로 만든다. 수입이 등록된 뒤
        // 처음 확인할 때만 다시 만들고, 정상적으로 생성된 이번 달 예산은 고정비 변경으로 바꾸지 않는다.
        if (!existing.isEmpty()) {
            boolean emptyPlan = existing.stream().mapToInt(UserStatBudget::getAmount).sum() == 0;
            if (!emptyPlan || Optional.ofNullable(user.getMonthlyIncome()).orElse(0) <= 0) return;
            statBudgets.deleteAll(existing);
            statBudgets.flush();
        }
        UserSpendingProfile profile = profiles.findById(userId).orElse(null);
        int income = Optional.ofNullable(user.getMonthlyIncome()).orElse(0);
        int fixed = fixedExpenses.findByUserIdOrderByIdAsc(userId).stream().mapToInt(FixedExpense::getAmount).sum();
        int reserve = Math.max(0, Math.round(income * 0.15f)); // 누락 고정비를 위한 안전금
        int available = Math.max(0, income - fixed - reserve);
        // 월수입 변경이 예산에 바로 반영되도록 실제 가용 수입을 기준으로 배분한다.
        // 설문의 자유지출 답은 소비 성향 데이터로만 보관하고 고정 상한으로 쓰지 않는다.
        EnumMap<StatType, Integer> weights = new EnumMap<>(StatType.class);
        weights.put(StatType.ENERGY, 30); weights.put(StatType.CHARM, 25); weights.put(StatType.IQ, 20); weights.put(StatType.ENDURANCE, 25);
        if (profile != null) {
            boost(weights, profile.getPrimarySpendArea());
            if (profile.getMealCostBand() == com.vori.backend.onboarding.MealCostBand.MEAL_10_15 || profile.getMealCostBand() == com.vori.backend.onboarding.MealCostBand.OVER_15) weights.merge(StatType.ENERGY, 5, Integer::sum);
        }
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        int assigned = 0;
        for (StatType type : StatType.values()) {
            int amount = type == StatType.ENDURANCE ? available - assigned : available * weights.get(type) / totalWeight;
            assigned += amount;
            statBudgets.save(UserStatBudget.builder().userId(userId).yearMonth(yearMonth).statType(type).amount(amount).build());
        }
    }
    /** 월 수입이 바뀐 경우에만 현재 월의 자동 예산을 새 수입으로 다시 배분한다. */
    @Transactional public void rebuildCurrentPlan(Long userId) {
        String yearMonth = YearMonth.now().toString();
        List<UserStatBudget> existing = statBudgets.findByUserIdAndYearMonth(userId, yearMonth);
        if (!existing.isEmpty()) {
            statBudgets.deleteAll(existing);
            statBudgets.flush();
        }
        ensurePlan(userId, yearMonth);
    }
    private void boost(Map<StatType,Integer> weights, PrimarySpendArea area) {
        if (area == null) return;
        switch (area) { case FOOD_CAFE -> weights.merge(StatType.ENERGY,10,Integer::sum); case SHOPPING_BEAUTY -> weights.merge(StatType.CHARM,10,Integer::sum); case CULTURE_LEISURE -> weights.merge(StatType.IQ,10,Integer::sum); case TRANSPORT_LIVING -> weights.merge(StatType.ENDURANCE,10,Integer::sum); default -> {} }
    }
    @Transactional(readOnly = true) public StatBudgetResponse plan(Long userId, String yearMonth) {
        Map<StatType,Integer> values = new EnumMap<>(StatType.class);
        statBudgets.findByUserIdAndYearMonth(userId,yearMonth).forEach(b -> values.put(b.getStatType(), b.getAmount()));
        int fixed = fixedExpenses.findByUserIdOrderByIdAsc(userId).stream().mapToInt(FixedExpense::getAmount).sum();
        int income = Optional.ofNullable(users.findById(userId).orElseThrow().getMonthlyIncome()).orElse(0);
        return new StatBudgetResponse(yearMonth, values, fixed, Math.round(income * .15f));
    }
    @Transactional public List<FixedExpenseResponse> listFixed(Long userId) { return fixedExpenses.findByUserIdOrderByIdAsc(userId).stream().map(FixedExpenseResponse::from).toList(); }
    @Transactional public FixedExpenseResponse addFixed(Long userId, FixedExpenseRequest req) { return FixedExpenseResponse.from(fixedExpenses.save(FixedExpense.builder().userId(userId).name(req.name().trim()).amount(req.amount()).build())); }
    @Transactional public void removeFixed(Long userId, Long id) { FixedExpense f=fixedExpenses.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"고정비를 찾을 수 없어요.")); if(!f.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"본인의 고정비만 삭제할 수 있어요."); fixedExpenses.delete(f); }
}
