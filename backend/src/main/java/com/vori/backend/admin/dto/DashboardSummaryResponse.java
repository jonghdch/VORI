package com.vori.backend.admin.dto;

import java.util.List;

/**
 * 어드민 종합 대시보드 첫 화면. 상단 KPI 6종 + 최근 7일 일별 가입 추이.
 */
public record DashboardSummaryResponse(
        long totalUsers,
        long newUsersToday,
        long adminCount,
        long totalSaved,
        long totalExpenses,
        long totalAiInquiries,
        List<DailySignup> signupTrend
) {
    /** 추이 차트의 하루치. date = "yyyy-MM-dd". */
    public record DailySignup(String date, long count) {}
}
