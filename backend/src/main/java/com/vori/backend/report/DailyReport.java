package com.vori.backend.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일일 리포트. 매일 1회 생성 (UNIQUE(user_id, report_date)).
 * 당일 수입·지출·절약·펫 성장 합계와 AI 코멘트, 펫 상태 스냅샷 보관.
 * 리포트는 캐릭터가 사용자에게 말 거는 톤으로 생성 — 단순 통계 X (VORI 스토리텔링 본질).
 */
@Entity
@Table(name = "daily_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "income_total", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer incomeTotal;

    @Column(name = "expense_total", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer expenseTotal;

    @Column(name = "saved_amount", nullable = false)
    private Integer savedAmount;

    @Column(name = "stat_delta_total")
    private Integer statDeltaTotal;

    // 리포트 생성 시점의 펫 상태 JSON 스냅샷. 나중에 펫 분양해도 리포트엔 그 시점 상태 보존
    @Column(name = "pet_snapshot", columnDefinition = "JSON")
    private String petSnapshot;

    @Column(name = "ai_comment", columnDefinition = "TEXT")
    private String aiComment;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
