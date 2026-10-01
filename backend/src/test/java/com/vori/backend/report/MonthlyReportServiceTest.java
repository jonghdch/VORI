package com.vori.backend.report;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.income.Income;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.judgment.DailyJudgment;
import com.vori.backend.judgment.DailyJudgmentRepository;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import com.vori.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 월간 리포트 정산 — 그 달 지출·수입·판정을 모아 한 번 저장하고 도착 알림을 보낸다. */
class MonthlyReportServiceTest {

    private static final long USER = 1L;
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    private final MonthlyReportRepository reports = mock(MonthlyReportRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final IncomeRepository incomes = mock(IncomeRepository.class);
    private final DailyJudgmentRepository judgments = mock(DailyJudgmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MonthlyReportService service =
            new MonthlyReportService(reports, expenses, incomes, judgments, users, notifications);

    @Test
    @DisplayName("한 달 합계와 판정 일수를 저장하고 도착 알림을 보낸다")
    void settles() {
        when(reports.findByUserIdAndYearMonth(USER, "2026-09")).thenReturn(Optional.empty());
        when(expenses.sumAmountInRange(eq(USER), any(), any())).thenReturn(120_000L);
        when(expenses.findByUserIdAndSpentAtBetween(eq(USER), any(), any())).thenReturn(List.of(
                com.vori.backend.expense.Expense.builder().userId(USER).amount(70_000).build(),
                com.vori.backend.expense.Expense.builder().userId(USER).amount(50_000).build()));
        when(incomes.findByUserIdAndReceivedAtBetween(eq(USER), any(), any())).thenReturn(List.of(
                Income.builder().userId(USER).amount(300_000).receivedAt(LocalDate.of(2026, 9, 25)).build()));
        when(judgments.findByUserIdAndJudgmentDateBetweenOrderByJudgmentDate(eq(USER), any(), any())).thenReturn(List.of(
                judgment(Signal.GREEN), judgment(Signal.GREEN), judgment(Signal.RED)));

        service.settle(USER, MONTH);

        ArgumentCaptor<MonthlyReport> saved = ArgumentCaptor.forClass(MonthlyReport.class);
        verify(reports).save(saved.capture());
        MonthlyReport r = saved.getValue();
        assertThat(r.getYearMonth()).isEqualTo("2026-09");
        assertThat(r.getExpenseTotal()).isEqualTo(120_000);
        assertThat(r.getExpenseCount()).isEqualTo(2);
        assertThat(r.getIncomeTotal()).isEqualTo(300_000);
        assertThat(r.getJudgedDays()).isEqualTo(3);
        assertThat(r.getGreenDays()).isEqualTo(2);
        assertThat(r.getRedDays()).isEqualTo(1);
        assertThat(r.getReadAt()).isNull();
        verify(notifications).notify(eq(USER), eq(NotificationType.MONTHLY_REPORT), any(),
                any(), eq("/report?month=2026-09"), eq("monthly-report:2026-09"));
    }

    @Test
    @DisplayName("이미 정산한 달은 다시 만들지 않는다")
    void idempotent() {
        when(reports.findByUserIdAndYearMonth(USER, "2026-09"))
                .thenReturn(Optional.of(MonthlyReport.builder().userId(USER).yearMonth("2026-09").build()));
        service.settle(USER, MONTH);
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("열람하면 readAt 이 찍힌다")
    void read() {
        MonthlyReport r = MonthlyReport.builder().userId(USER).yearMonth("2026-09").build();
        when(reports.findByUserIdAndYearMonth(USER, "2026-09")).thenReturn(Optional.of(r));
        service.markRead(USER, "2026-09");
        assertThat(r.getReadAt()).isNotNull();
    }

    private static DailyJudgment judgment(Signal s) {
        return DailyJudgment.builder().userId(USER).signal(s).judgmentDate(LocalDate.of(2026, 9, 10))
                .judgedAt(LocalDateTime.now()).build();
    }
}
