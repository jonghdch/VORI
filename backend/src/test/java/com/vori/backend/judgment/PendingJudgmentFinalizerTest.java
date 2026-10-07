package com.vori.backend.judgment;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** 자정 자동 확정 — 지난 날짜의 PENDING 판정만 처리하고, 한 건이 실패해도 나머지는 계속한다. */
class PendingJudgmentFinalizerTest {
    private final DailyJudgmentRepository judgments = mock(DailyJudgmentRepository.class);
    private final DailyJudgmentService service = mock(DailyJudgmentService.class);
    private final PendingJudgmentFinalizer finalizer = new PendingJudgmentFinalizer(judgments, service);

    @Test
    void 지난_날짜의_미확정_판정을_모두_확정하고_실패한_건은_건너뛴다() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        DailyJudgment a = DailyJudgment.builder().id(1L).userId(1L).judgmentDate(today.minusDays(1)).status(JudgmentStatus.PENDING).build();
        DailyJudgment b = DailyJudgment.builder().id(2L).userId(2L).judgmentDate(today.minusDays(1)).status(JudgmentStatus.PENDING).build();
        DailyJudgment c = DailyJudgment.builder().id(3L).userId(3L).judgmentDate(today.minusDays(2)).status(JudgmentStatus.PENDING).build();
        when(judgments.findByStatusAndJudgmentDateBefore(JudgmentStatus.PENDING, today)).thenReturn(List.of(a, b, c));
        when(service.finalizeExpiredAsInitial(1L)).thenReturn(true);
        when(service.finalizeExpiredAsInitial(2L)).thenThrow(new IllegalStateException("boom"));
        when(service.finalizeExpiredAsInitial(3L)).thenReturn(true);

        assertEquals(2, finalizer.finalizeExpired(today));

        verify(service).finalizeExpiredAsInitial(3L);
        verify(judgments).findByStatusAndJudgmentDateBefore(JudgmentStatus.PENDING, today);
    }
}
