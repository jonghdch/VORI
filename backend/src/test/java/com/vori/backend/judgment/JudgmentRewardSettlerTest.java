package com.vori.backend.judgment;

import com.vori.backend.expense.ExpenseRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 자정 정산 — 미확정 확정 → 판정 안 한 날 대신 판정 → 보상 지급 순서로, 한 건이 실패해도 나머지는 계속한다. */
class JudgmentRewardSettlerTest {
    private final DailyJudgmentRepository judgments = mock(DailyJudgmentRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final DailyJudgmentService service = mock(DailyJudgmentService.class);
    private final JudgmentRewardSettler settler = new JudgmentRewardSettler(judgments, expenses, service);

    private static DailyJudgment row(long id, LocalDate date, JudgmentStatus status) {
        return DailyJudgment.builder().id(id).userId(id).judgmentDate(date).status(status).build();
    }

    @Test
    void 미확정을_확정하고_판정_안_한_날을_대신_판정한_뒤_보상을_지급한다() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        LocalDate yesterday = today.minusDays(1);
        when(judgments.findByStatusAndJudgmentDateBefore(JudgmentStatus.PENDING, today))
                .thenReturn(List.of(row(1L, yesterday, JudgmentStatus.PENDING)));
        when(expenses.findUserIdsWithExpenseInRange(yesterday.atStartOfDay(), today.atStartOfDay()))
                .thenReturn(List.of(5L));
        when(judgments.findByStatusAndRewardedAtIsNullAndJudgmentDateBefore(JudgmentStatus.FINALIZED, today))
                .thenReturn(List.of(row(1L, yesterday, JudgmentStatus.FINALIZED), row(2L, yesterday, JudgmentStatus.FINALIZED),
                        row(3L, yesterday.minusDays(1), JudgmentStatus.FINALIZED)));
        when(service.payReward(1L)).thenReturn(true);
        when(service.payReward(2L)).thenThrow(new IllegalStateException("boom"));
        when(service.payReward(3L)).thenReturn(true);

        assertEquals(2, settler.settle(today));

        var order = inOrder(service);
        order.verify(service).finalizeExpiredAsInitial(1L);
        order.verify(service).judgeUnjudgedDay(5L, yesterday);
        order.verify(service).payReward(1L);
        verify(service).payReward(3L); // 2번이 실패해도 계속한다
    }

    @Test
    void 판정_안_한_날은_어제_하루만_본다() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        settler.settle(today);
        verify(expenses).findUserIdsWithExpenseInRange(today.minusDays(1).atStartOfDay(), today.atStartOfDay());
        verify(expenses, times(1)).findUserIdsWithExpenseInRange(any(), any());
    }
}
