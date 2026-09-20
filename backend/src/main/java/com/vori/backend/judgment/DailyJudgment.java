package com.vori.backend.judgment;

import com.vori.backend.expense.Signal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_judgments", uniqueConstraints =
        @UniqueConstraint(name = "uk_daily_judgment_user_date", columnNames = {"user_id", "judgment_date"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DailyJudgment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "judgment_date", nullable = false)
    private LocalDate judgmentDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "result_signal", nullable = false,
            columnDefinition = "ENUM('RED','GRAY','GREEN')")
    private Signal signal;
    @Column(name = "expense_count", nullable = false)
    private Integer expenseCount;
    @Column(name = "judged_at", nullable = false)
    private LocalDateTime judgedAt;
}
