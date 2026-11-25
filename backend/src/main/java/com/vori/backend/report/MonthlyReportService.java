package com.vori.backend.report;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.income.Income;
import com.vori.backend.income.IncomeRepository;
import com.vori.backend.judgment.DailyJudgment;
import com.vori.backend.judgment.DailyJudgmentRepository;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /** 사용자 요청 정산이 받는 가장 오래된 달(현재 달 기준 몇 달 전까지). */
    private static final int MAX_MONTHS_BACK = 12;

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

    /**
     * 사용자가 직접 부르는 정산(시연·확인용). 끝나지 않은 달을 미리 만들면 settle 이 이미 있는 리포트로 보고
     * 말일 정산을 건너뛰어 중간 집계가 굳으므로, current 보다 앞선 달만 받는다.
     */
    @Transactional
    public boolean settleClosedMonth(Long userId, YearMonth month, YearMonth current) {
        if (!month.isBefore(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지난 달 리포트만 정산할 수 있습니다");
        }
        // 하한이 없으면 month=0001-01 같은 요청마다 빈 리포트와 알림이 생긴다. 가입일로 자르면
        // 가입 전 날짜로 적은 지출이 있는 달을 못 만들어서, 최근 12개월로 자른다
        if (month.isBefore(current.minusMonths(MAX_MONTHS_BACK))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "최근 " + MAX_MONTHS_BACK + "개월 리포트만 정산할 수 있습니다");
        }
        return settle(userId, month);
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
