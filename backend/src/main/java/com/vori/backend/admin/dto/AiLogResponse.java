package com.vori.backend.admin.dto;

import com.vori.backend.inquiry.AiInquiry;
import com.vori.backend.inquiry.ReasonCategory;

import java.time.LocalDateTime;

/**
 * 어드민 AI 대사 로그 한 행. AI 가 물은 사유 질문 + 사용자 답변 + 분류 결과.
 */
public record AiLogResponse(
        Long id,
        Long userId,
        Long expenseId,
        String question,
        String answerText,
        ReasonCategory reasonCategory,
        Boolean signalAdjusted,
        LocalDateTime askedAt,
        LocalDateTime answeredAt
) {
    public static AiLogResponse from(AiInquiry i) {
        return new AiLogResponse(
                i.getId(),
                i.getUserId(),
                i.getExpenseId(),
                i.getQuestion(),
                i.getAnswerText(),
                i.getReasonCategory(),
                i.getSignalAdjusted(),
                i.getAskedAt(),
                i.getAnsweredAt()
        );
    }
}
