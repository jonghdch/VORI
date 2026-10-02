package com.vori.backend.report;

import java.time.LocalDateTime;

public record MonthlyReportResponse(
        String yearMonth,
        int expenseTotal,
        int expenseCount,
        int incomeTotal,
        int judgedDays,
        int greenDays,
        int grayDays,
        int redDays,
        LocalDateTime generatedAt,
        boolean read
) {
    static MonthlyReportResponse from(MonthlyReport r) {
        return new MonthlyReportResponse(r.getYearMonth(), r.getExpenseTotal(), r.getExpenseCount(),
                r.getIncomeTotal(), r.getJudgedDays(), r.getGreenDays(), r.getGrayDays(), r.getRedDays(),
                r.getGeneratedAt(), r.getReadAt() != null);
    }
}
