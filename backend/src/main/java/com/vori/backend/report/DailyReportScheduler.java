package com.vori.backend.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 일일 리포트 자동 생성 트리거.
 *
 * 자정 직후에 전일(D-1) 리포트를 만든다. 설계서에는 "아침 7시"로 적혀 있지만 그건 푸시 발송
 * 시점이고, 집계 자체는 하루가 끝나자마자 해두는 편이 맞다 — 사용자가 아침에 앱을 열었을 때
 * 그 자리에서 AI 를 호출해 수 초를 기다리게 하지 않으려는 것.
 *
 * 배치가 실패해도 서버는 계속 떠 있어야 하므로 예외를 밖으로 내보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReportScheduler {

    private final DailyReportService dailyReportService;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void generateYesterdayReports() {
        LocalDate target = LocalDate.now().minusDays(1);
        try {
            DailyReportService.BatchResult result = dailyReportService.generateFor(target);
            log.info("일일 리포트 스케줄 완료 — {}", result);
        } catch (Exception e) {
            log.error("일일 리포트 스케줄 실패 — date={}", target, e);
        }
    }
}
