package com.vori.backend.judgment;

import com.vori.backend.expense.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 자정 정산 (docs/judgment-flow.md ⑤) — 지난 날의 판정을 마무리하고 보상을 한 번 지급한다.
 *
 * 1. 사유 입력을 끝내지 않은(PENDING) 판정 → 1차 신호등 판정 내용으로 확정(D7)
 * 2. 어제 지출을 적었는데 판정 받기를 하지 않은 사용자 → 신호등 판정을 대신 내려 확정(사유 반영 없음)
 * 3. 확정됐는데 아직 보상을 주지 않은 판정 → 판정 때 정해 둔 보상을 지급
 *
 * 매일 00:00(한국 시간)에 돌고, 자정에 서버가 꺼져 있었던 경우에 대비해 서버가 뜰 때도 한 번 돈다.
 * 2번은 어제 하루만 본다 — 배포 첫날 지난 모든 날짜에 판정·보상이 한꺼번에 생기지 않게.
 * 한 건이 실패해도 나머지는 계속 처리하고 ERROR 로그를 남긴다. 실패한 건은 다음 실행 때 다시 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgmentRewardSettler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyJudgmentRepository judgments;
    private final ExpenseRepository expenses;
    private final DailyJudgmentService service;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void settleAtMidnight() {
        settle(LocalDate.now(KST));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void settleOnStartup() {
        settle(LocalDate.now(KST));
    }

    /** today 이전 날짜를 정산한다. 보상을 지급한 건수를 돌려준다. */
    int settle(LocalDate today) {
        int finalized = 0, judged = 0, paid = 0, failed = 0;
        for (DailyJudgment row : judgments.findByStatusAndJudgmentDateBefore(JudgmentStatus.PENDING, today)) {
            try {
                if (service.finalizeExpiredAsInitial(row.getId())) finalized++;
            } catch (Exception e) {
                failed++;
                log.error("자정 정산 — 미확정 판정 확정 실패 judgmentId={}, userId={}, date={}", row.getId(), row.getUserId(), row.getJudgmentDate(), e);
            }
        }
        LocalDate yesterday = today.minusDays(1);
        for (Long userId : expenses.findUserIdsWithExpenseInRange(yesterday.atStartOfDay(), today.atStartOfDay())) {
            try {
                if (service.judgeUnjudgedDay(userId, yesterday)) judged++;
            } catch (Exception e) {
                failed++;
                log.error("자정 정산 — 판정 안 한 날 판정 실패 userId={}, date={}", userId, yesterday, e);
            }
        }
        for (DailyJudgment row : judgments.findByStatusAndRewardedAtIsNullAndJudgmentDateBefore(JudgmentStatus.FINALIZED, today)) {
            try {
                if (service.payReward(row.getId())) paid++;
            } catch (Exception e) {
                failed++;
                log.error("자정 정산 — 보상 지급 실패 judgmentId={}, userId={}, date={}", row.getId(), row.getUserId(), row.getJudgmentDate(), e);
            }
        }
        if (finalized + judged + paid + failed > 0) {
            log.info("자정 정산 — before={}, 미확정 확정={}, 대신 판정={}, 보상 지급={}, 실패={}", today, finalized, judged, paid, failed);
        }
        return paid;
    }
}
