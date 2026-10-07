package com.vori.backend.judgment;

import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.notification.NotificationService;
import com.vori.backend.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 22시(소비 판정이 열리는 시각) — 오늘 지출을 기록했고 아직 판정하지 않은 사용자에게 알린다.
 * 열리는 시각은 서버 vori.ai-judge.open-hour·프론트 AI_ACTIVE_FROM_HOUR 기본값(22)과 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgmentOpenNotifier {

    private final ExpenseRepository expenseRepository;
    private final DailyJudgmentRepository dailyJudgmentRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void notifyOpen() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        try {
            int n = 0;
            for (Long userId : expenseRepository.findUserIdsWithExpenseInRange(
                    today.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
                if (dailyJudgmentRepository.findByUserIdAndJudgmentDate(userId, today).isPresent()) continue;
                notificationService.notify(userId, NotificationType.JUDGMENT_OPEN,
                        "오늘 소비를 판정할 수 있어요", "판정하면 오늘 자정에 코인과 펫 경험치를 받아요",
                        "/wallet", "judgment-open:" + today);
                n++;
            }
            log.info("소비 판정 열림 알림 — date={}, users={}", today, n);
        } catch (Exception e) {
            log.error("소비 판정 열림 알림 실패 — date={}", today, e);
        }
    }
}
