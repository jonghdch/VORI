package com.vori.backend.judgment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 자정 — 그날 안에 사유 입력을 끝내지 않은(PENDING) 판정을 1차 판정 내용으로 확정하고 보상을 지급한다
 * (docs/judgment-flow.md D7). 예외 지출 사유는 반영하지 않는다.
 *
 * 자정에 서버가 꺼져 있었으면 그 판정이 PENDING 으로 남으므로, 서버가 뜰 때도 한 번 돌려 밀린 것을 처리한다.
 * 한 건이 실패해도 나머지는 계속 처리하고, 실패는 ERROR 로그로 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingJudgmentFinalizer {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyJudgmentRepository judgments;
    private final DailyJudgmentService service;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void finalizeAtMidnight() {
        finalizeExpired(LocalDate.now(KST));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void finalizeOnStartup() {
        finalizeExpired(LocalDate.now(KST));
    }

    /** today 이전 날짜의 PENDING 판정을 모두 확정한다. 확정한 건수를 돌려준다. */
    int finalizeExpired(LocalDate today) {
        int done = 0, failed = 0;
        for (DailyJudgment row : judgments.findByStatusAndJudgmentDateBefore(JudgmentStatus.PENDING, today)) {
            try {
                if (service.finalizeExpiredAsInitial(row.getId())) done++;
            } catch (Exception e) {
                failed++;
                log.error("미확정 판정 자동 확정 실패 — judgmentId={}, userId={}, date={}",
                        row.getId(), row.getUserId(), row.getJudgmentDate(), e);
            }
        }
        if (done > 0 || failed > 0) log.info("미확정 판정 자동 확정 — before={}, done={}, failed={}", today, done, failed);
        return done;
    }
}
