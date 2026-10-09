package com.vori.backend.report;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.judgment.DailyJudgment;
import com.vori.backend.judgment.DailyJudgmentRepository;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 월간(보이는) 리포트 정산. 매월 마지막 날 12시(MonthlyReportScheduler)에 그 달 요약을 저장하고
 * "○월 리포트가 도착했어요" 알림을 보낸다. 같은 달은 한 번만 정산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportService {

    private final MonthlyReportRepository monthlyReportRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final DailyJudgmentRepository dailyJudgmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /** 모든 사용자의 그 달 리포트를 정산한다. 한 명이 실패해도 나머지는 계속. */
    public int settleAll(YearMonth month) {
        int done = 0;
        for (User user : userRepository.findAll()) {
            if (user.isPendingDeletion()) continue; // 탈퇴 대기 계정 — 곧 지워질 리포트를 만들지 않는다
            try {
                if (settle(user.getId(), month)) done++;
            } catch (Exception e) {
                log.error("월간 리포트 정산 실패 — userId={}, month={}", user.getId(), month, e);
            }
        }
        return done;
    }

    /** 한 사용자의 그 달 리포트를 정산한다. 이미 있으면 false. */
    @Transactional
    public boolean settle(Long userId, YearMonth month) {
        String key = month.toString();
        if (monthlyReportRepository.findByUserIdAndYearMonth(userId, key).isPresent()) return false;

        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        long expenseTotal = expenseRepository.sumAmountInRange(userId, start, end);
        int expenseCount = expenseRepository.findByUserIdAndSpentAtBetween(userId, start, end).size();
        long incomeTotal = incomeRepository
                .findByUserIdAndReceivedAtBetween(userId, month.atDay(1), month.atEndOfMonth()).stream()
                .mapToLong(i -> i.getAmount() == null ? 0 : i.getAmount())
                .sum();
        List<DailyJudgment> judged = dailyJudgmentRepository
                .findByUserIdAndJudgmentDateBetweenOrderByJudgmentDate(userId, month.atDay(1), month.atEndOfMonth())
                .stream().filter(DailyJudgment::isFinalized).toList(); // 사유를 기다리는 1차 판정은 아직 그날 결과가 아니다

        monthlyReportRepository.save(MonthlyReport.builder()
                .userId(userId)
                .yearMonth(key)
                .expenseTotal((int) expenseTotal)
                .expenseCount(expenseCount)
                .incomeTotal((int) incomeTotal)
                .judgedDays(judged.size())
                .greenDays(count(judged, Signal.GREEN))
                .grayDays(count(judged, Signal.GRAY))
                .redDays(count(judged, Signal.RED))
                .generatedAt(LocalDateTime.now())
                .build());

        notificationService.notify(userId, NotificationType.MONTHLY_REPORT,
                month.getMonthValue() + "월 리포트가 도착했어요",
                "한 달 소비를 함께 돌아봐요", "/report?month=" + key, "monthly-report:" + key);
        return true;
    }

    @Transactional(readOnly = true)
    public List<MonthlyReportResponse> list(Long userId) {
        return monthlyReportRepository.findByUserIdOrderByYearMonthDesc(userId).stream()
                .map(MonthlyReportResponse::from).toList();
    }

    /** 아직 열람하지 않은 가장 최근 리포트. 없으면 empty. */
    @Transactional(readOnly = true)
    public Optional<MonthlyReportResponse> latestUnread(Long userId) {
        return monthlyReportRepository.findFirstByUserIdAndReadAtIsNullOrderByYearMonthDesc(userId)
                .map(MonthlyReportResponse::from);
    }

    @Transactional
    public void markRead(Long userId, String yearMonth) {
        monthlyReportRepository.findByUserIdAndYearMonth(userId, yearMonth)
                .ifPresent(r -> r.markRead(LocalDateTime.now()));
    }

    private static int count(List<DailyJudgment> judged, Signal signal) {
        return (int) judged.stream().filter(j -> j.getSignal() == signal).count();
    }
}
