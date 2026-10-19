package com.vori.backend.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 월간(보이는) 리포트 정산 기록 — 매월 마지막 날 12시에 그 달 요약을 고정해 둔다.
 * readAt 이 NULL 이면 아직 열람하지 않은 리포트(홈 말풍선·알림이 확인을 권한다).
 */
@Entity
@Table(name = "monthly_reports")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** "2026-09" */
    @Column(name = "report_month", nullable = false, columnDefinition = "CHAR(7)")
    private String yearMonth;

    @Column(name = "expense_total", nullable = false)
    @Builder.Default
    private Integer expenseTotal = 0;

    @Column(name = "expense_count", nullable = false)
    @Builder.Default
    private Integer expenseCount = 0;

    @Column(name = "income_total", nullable = false)
    @Builder.Default
    private Integer incomeTotal = 0;

    @Column(name = "judged_days", nullable = false)
    @Builder.Default
    private Integer judgedDays = 0;

    @Column(name = "green_days", nullable = false)
    @Builder.Default
    private Integer greenDays = 0;

    @Column(name = "gray_days", nullable = false)
    @Builder.Default
    private Integer grayDays = 0;

    @Column(name = "red_days", nullable = false)
    @Builder.Default
    private Integer redDays = 0;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public void markRead(LocalDateTime at) {
        if (readAt == null) readAt = at;
    }

    /** 달이 끝나기 전에 만들어져 중간 집계로 굳은 리포트를 말일 기준 합계로 고친다. 읽음 표시는 그대로 둔다. */
    public void refresh(MonthlyReport totals) {
        expenseTotal = totals.expenseTotal;
        expenseCount = totals.expenseCount;
        incomeTotal = totals.incomeTotal;
        judgedDays = totals.judgedDays;
        greenDays = totals.greenDays;
        grayDays = totals.grayDays;
        redDays = totals.redDays;
        generatedAt = totals.generatedAt;
    }
}
