package com.vori.backend.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 월간(보이는) 리포트 정산 트리거 — 매월 마지막 날 12시(L = 그 달의 마지막 날).
 * 그날 12시 이후 기록은 그 달 리포트에 들어가지 않는다. 실패해도 서버는 계속 떠 있어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {

    private final MonthlyReportService monthlyReportService;

    @Scheduled(cron = "0 0 12 L * *", zone = "Asia/Seoul")
    public void settleThisMonth() {
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Seoul"));
        try {
            int n = monthlyReportService.settleAll(month);
            log.info("월간 리포트 정산 완료 — month={}, settled={}", month, n);
        } catch (Exception e) {
            log.error("월간 리포트 정산 실패 — month={}", month, e);
        }
    }
}
