package com.vori.backend.home;

import com.vori.backend.expense.Signal;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 홈 대시보드 데이터 한 번에. 스탯 + 기간별 지출 합계 + 최근 지출 + 이번 달 카테고리 분포.
 * (업적·펫은 별도 도메인이라 미포함 — 프론트 정적 유지)
 */
public record HomeSummaryResponse(
        Stats stats,
        Spending spending,
        List<RecentExpense> recentExpenses,
        List<CategorySlice> categoryBreakdown
) {
    /** 누적 스탯 4종 + 총 절약액. */
    public record Stats(int energy, int charm, int iq, int endurance, int totalSaved) {}

    /** 기간별 지출 합계(원). */
    public record Spending(long today, long thisWeek, long thisMonth) {}

    public record RecentExpense(
            Long id,
            String item,
            int amount,
            String categoryName,
            Signal signalFinal,
            LocalDateTime spentAt
    ) {}

    /** 이번 달 카테고리별 지출. 비율은 프론트에서 계산. */
    public record CategorySlice(String categoryName, long amount) {}
}
