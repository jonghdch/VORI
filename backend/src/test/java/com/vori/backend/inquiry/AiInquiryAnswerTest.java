package com.vori.backend.inquiry;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.ExpenseRepository;
import com.vori.backend.expense.Signal;
import com.vori.backend.gemini.GeminiClient;
import com.vori.backend.inquiry.dto.AnswerRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 답변 제출은 AI 가 막혀도 되어야 한다 — 10/7 실측에서 Gemini 실패가 그대로 500 이 되어 답변이 저장되지 않았다.
 * 사유 칩을 고르면 AI 를 부르지 않고, 글만 쓰면 AI → (실패 시) 낱말 규칙 → 기타.
 */
class AiInquiryAnswerTest {

    private static final long USER = 3L;
    private static final long INQUIRY = 11L;

    private final AiInquiryRepository repo = mock(AiInquiryRepository.class);
    private final ExpenseRepository expenses = mock(ExpenseRepository.class);
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final AiInquiryService service = new AiInquiryService(
            repo, expenses, null, gemini, new TransactionTemplate(txManager), mock(ApplicationEventPublisher.class));

    private AiInquiry inquiry;
    private Expense expense;

    @BeforeEach
    void 빨강_지출과_대기_질문() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        inquiry = AiInquiry.pending(7L, USER, "결혼식 축의금", 200_000);
        expense = Expense.builder().id(7L).userId(USER).item("결혼식 축의금").amount(200_000)
                .signalInitial(Signal.RED).signalFinal(Signal.RED).build();
        when(repo.findById(INQUIRY)).thenReturn(Optional.of(inquiry));
        when(repo.findByIdForUpdate(INQUIRY)).thenReturn(Optional.of(inquiry));
        when(expenses.findById(7L)).thenReturn(Optional.of(expense));
    }

    @Test
    @DisplayName("칩만 고르면 AI 를 부르지 않고 칩 문구를 답변으로 남긴다")
    void chipOnly() {
        service.answerInquiry(INQUIRY, USER, new AnswerRequest(null, ReasonCategory.CEREMONY));

        verify(gemini, never()).classifyAnswer(anyString(), anyString());
        assertThat(inquiry.getReasonCategory()).isEqualTo(ReasonCategory.CEREMONY);
        assertThat(inquiry.getAnswerText()).isEqualTo("경조사");
        assertThat(inquiry.getAnsweredAt()).isNotNull();
        assertThat(expense.getSignalFinal()).isEqualTo(Signal.GREEN);
    }

    @Test
    @DisplayName("칩과 글이 둘 다 있으면 칩이 우선이고 글은 그대로 남긴다")
    void chipWinsOverText() {
        service.answerInquiry(INQUIRY, USER, new AnswerRequest("  친구 결혼식이라서 ", ReasonCategory.SOCIAL));

        verify(gemini, never()).classifyAnswer(anyString(), anyString());
        assertThat(inquiry.getReasonCategory()).isEqualTo(ReasonCategory.SOCIAL);
        assertThat(inquiry.getAnswerText()).isEqualTo("친구 결혼식이라서");
        assertThat(expense.getSignalFinal()).isEqualTo(Signal.GRAY);
    }

    @Test
    @DisplayName("글만 쓰면 AI 가 분류한다")
    void textUsesAi() {
        when(gemini.classifyAnswer(anyString(), anyString())).thenReturn(ReasonCategory.EMERGENCY);

        service.answerInquiry(INQUIRY, USER, new AnswerRequest("갑자기 아파서", null));

        assertThat(inquiry.getReasonCategory()).isEqualTo(ReasonCategory.EMERGENCY);
        assertThat(expense.getSignalFinal()).isEqualTo(Signal.GREEN);
    }

    @Test
    @DisplayName("AI 가 실패해도 답변은 저장되고, 낱말 규칙으로 사유를 고른다")
    void aiFailureFallsBackToWords() {
        when(gemini.classifyAnswer(anyString(), anyString()))
                .thenThrow(new RuntimeException("AI 서비스 호출에 실패했습니다."));

        service.answerInquiry(INQUIRY, USER, new AnswerRequest("친구 결혼식 축의금", null));

        assertThat(inquiry.getAnsweredAt()).isNotNull();
        assertThat(inquiry.getReasonCategory()).isEqualTo(ReasonCategory.CEREMONY);
        assertThat(expense.getSignalFinal()).isEqualTo(Signal.GREEN);
    }

    @Test
    @DisplayName("AI 가 실패하고 낱말로도 모르면 기타 — 답변은 저장, 신호는 그대로")
    void aiFailureAndUnknownWordsKeepsSignal() {
        when(gemini.classifyAnswer(anyString(), anyString()))
                .thenThrow(new RuntimeException("AI 서비스 호출에 실패했습니다."));

        service.answerInquiry(INQUIRY, USER, new AnswerRequest("그때는 그럴 일이 있었어", null));

        assertThat(inquiry.getAnsweredAt()).isNotNull();
        assertThat(inquiry.getReasonCategory()).isEqualTo(ReasonCategory.ETC);
        assertThat(expense.getSignalFinal()).isEqualTo(Signal.RED);
    }

    @Test
    @DisplayName("칩도 글도 없으면 검증에서 막는다")
    void needsChipOrText() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new AnswerRequest(null, null))).isNotEmpty();
        assertThat(validator.validate(new AnswerRequest("   ", null))).isNotEmpty();
        assertThat(validator.validate(new AnswerRequest(null, ReasonCategory.ETC))).isEmpty();
        assertThat(validator.validate(new AnswerRequest("친구 생일", null))).isEmpty();
        assertThat(validator.validate(new AnswerRequest("가".repeat(501), null))).isNotEmpty();
    }
}
