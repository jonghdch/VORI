package com.vori.backend.inquiry;

import com.vori.backend.common.StatType;
import com.vori.backend.expense.ExpenseAnomalyEvent;
import com.vori.backend.expense.Signal;
import com.vori.backend.gemini.GeminiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RED 지출의 질문 행이 Gemini 결과와 무관하게 존재하고, AI 문구는 나중에 덮어쓰기만 하는지 본다.
 * 회귀 대상: Gemini 가 재시도 끝에 실패하면 그 지출은 영영 질문이 없던 문제.
 */
class AiInquiryPendingQuestionTest {

    private final AiInquiryRepository repo = mock(AiInquiryRepository.class);
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final AiInquiryService service = new AiInquiryService(
            repo, null, null, gemini, null, null);

    private static ExpenseAnomalyEvent event() {
        return new ExpenseAnomalyEvent(11L, 7L, 3L, "결혼식 축의금", 200_000, StatType.ENERGY,
                new BigDecimal("8000"), Signal.RED);
    }

    @Test
    @DisplayName("대기 질문은 내역과 금액이 들어간 템플릿 문구로 만들어진다")
    void pendingHasTemplateQuestion() {
        AiInquiry pending = AiInquiry.pending(7L, 3L, "결혼식 축의금", 200_000);

        assertThat(pending.getExpenseId()).isEqualTo(7L);
        assertThat(pending.getUserId()).isEqualTo(3L);
        assertThat(pending.getQuestion()).contains("결혼식 축의금").contains("200,000원");
        assertThat(pending.getAskedAt()).isNotNull();
        assertThat(pending.getAnsweredAt()).isNull();
    }

    @Test
    @DisplayName("내역이 비어 있어도 질문 문구가 비지 않는다")
    void templateSurvivesBlankItem() {
        assertThat(AiInquiry.templateQuestion("  ", null)).isNotBlank();
        assertThat(AiInquiry.templateQuestion(null, 5000)).contains("5,000원");
    }

    @Test
    @DisplayName("Gemini 가 성공하면 그 이벤트의 질문 행(id)만 조건 UPDATE 로 바꾼다")
    void refinesWithAiQuestion() {
        when(gemini.generateQuestion(anyString(), anyInt(), any(), any())).thenReturn("무슨 일이었어?");
        when(repo.updateQuestionIfUnanswered(11L, "무슨 일이었어?")).thenReturn(1);

        service.handleAnomalyEvent(event());

        verify(repo).updateQuestionIfUnanswered(11L, "무슨 일이었어?");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("Gemini 가 실패해도 예외 없이 끝나고 질문 행은 건드리지 않는다(템플릿 유지)")
    void keepsTemplateWhenGeminiFails() {
        when(gemini.generateQuestion(anyString(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("AI 서비스 호출에 실패했습니다."));

        service.handleAnomalyEvent(event());

        verify(repo, never()).updateQuestionIfUnanswered(anyLong(), anyString());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("답한 뒤거나 행이 지워졌으면(UPDATE 0행) false 로 끝나고 아무것도 저장하지 않는다")
    void answeredOrDeletedRowIsLeftAlone() {
        when(repo.updateQuestionIfUnanswered(11L, "무슨 일이었어?")).thenReturn(0);

        assertThat(service.refineQuestion(11L, "무슨 일이었어?")).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("빈 AI 문구나 없는 행 id 로는 UPDATE 를 시도하지 않는다")
    void blankQuestionIsIgnored() {
        assertThat(service.refineQuestion(11L, "  ")).isFalse();
        assertThat(service.refineQuestion(null, "무슨 일이었어?")).isFalse();
        verify(repo, never()).updateQuestionIfUnanswered(anyLong(), anyString());
    }
}
