package com.vori.backend.report;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일일 리포트 한 건. aiComment 가 null 이면 AI 호출이 실패한 것 —
 * 통계는 정상이므로 화면에서는 코멘트 영역만 비우면 된다.
 */
public record DailyReportResponse(
        Long id,
        LocalDate reportDate,
        int incomeTotal,
        int expenseTotal,
        int savedAmount,
        Integer statDeltaTotal,
        String petSnapshot,
        String aiComment,
        LocalDateTime generatedAt,
        LocalDateTime readAt
) {
    public static DailyReportResponse from(DailyReport r) {
        return new DailyReportResponse(
                r.getId(),
                r.getReportDate(),
                r.getIncomeTotal(),
                r.getExpenseTotal(),
                r.getSavedAmount(),
                r.getStatDeltaTotal(),
                r.getPetSnapshot(),
                r.getAiComment(),
                r.getGeneratedAt(),
                r.getReadAt());
    }
}
