package com.vori.backend.report;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Column(name = "pet_snapshot", columnDefinition = "JSON")
    private String petSnapshot;

    @Column(name = "ai_comment", columnDefinition = "TEXT")
    private String aiComment;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
