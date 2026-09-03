package com.vori.backend.budget.dto;

import com.vori.backend.budget.MonthlyBudget;

/**
 * 월 예산과 그 달의 사용 현황.
 *
 * 예산만 돌려주면 화면이 지출 합계를 따로 조회해 직접 나눠야 한다. 사용률·잔액까지 서버가
 * 계산해 내려주면 홈 화면이 게이지를 바로 그릴 수 있고, 0 나누기도 신경 쓸 필요가 없다.
 *
 * 예산을 설정하지 않은 달은 amount=0 · budgetSet=false 로 내려간다 — 404 대신 "미설정" 상태를
 * 표현해 화면이 예외 분기 없이 "예산을 설정해보세요" 를 띄울 수 있게 한다.
 */
public record BudgetResponse(
        Long id,
        String yearMonth,
        int amount,
        long spent,
        long remaining,
        int usagePct,
        boolean exceeded,
        boolean budgetSet
) {
    public static BudgetResponse of(MonthlyBudget budget, long spent) {
        int amount = budget.getAmount() == null ? 0 : budget.getAmount();
        return new BudgetResponse(
                budget.getId(),
                budget.getYearMonth(),
                amount,
                spent,
                amount - spent,          // 초과하면 음수 — 화면에서 "N원 초과" 로 쓸 수 있다
                usagePct(amount, spent),
                spent > amount,
                true);
    }

    /** 아직 예산을 정하지 않은 달. 지출 합계는 그대로 알려준다. */
    public static BudgetResponse unset(String yearMonth, long spent) {
        return new BudgetResponse(null, yearMonth, 0, spent, 0, 0, false, false);
    }

    /** 100 을 넘겨 보내야 화면이 "초과" 를 표현할 수 있으므로 상한을 두지 않는다. */
    private static int usagePct(int amount, long spent) {
        if (amount <= 0) return 0;
        return (int) (spent * 100 / amount);
    }
}
