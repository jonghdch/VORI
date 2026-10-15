package com.vori.backend.inquiry.dto;

import com.vori.backend.inquiry.ReasonCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * AI 질문 답변. 사유 칩(reasonCategory)이나 글(answerText) 중 하나는 있어야 한다.
 * 칩을 고르면 그 사유를 그대로 쓰고(AI 안 씀), 글만 쓰면 AI 가 분류한다(실패하면 낱말 규칙).
 * 둘 다 있으면 칩이 우선이다.
 */
public record AnswerRequest(
        @Size(max = 500, message = "답변은 최대 500자까지 입력 가능합니다.")
        String answerText,

        ReasonCategory reasonCategory
) {
    @AssertTrue(message = "사유를 고르거나 답변을 적어주세요.")
    public boolean isAnswered() {
        return reasonCategory != null || hasText();
    }

    public boolean hasText() {
        return answerText != null && !answerText.isBlank();
    }
}
