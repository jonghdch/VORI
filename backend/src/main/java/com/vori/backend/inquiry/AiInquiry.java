package com.vori.backend.inquiry;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 가 사용자에게 지출 사유를 물어본 기록. expense_id UQ — 지출 1건당 질문 1개.
 * signal_initial ∈ {RED, GRAY} 일 때 트리거. 답변 받아 reason_category 분류 → signal_final 보정.
 * 보정 매핑 표는 docs/domain.md 참조 (TBD).
 */
@Entity
@Table(name = "ai_inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false, unique = true)
    private Long expenseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category",
            columnDefinition = "ENUM('CEREMONY','EMERGENCY','SOCIAL','SELF_INVEST','IMPULSE','ETC')")
    private ReasonCategory reasonCategory;

    // 보정이 실제 일어났는지 (signal_initial != signal_final 인 경우 TRUE)
    @Column(name = "signal_adjusted")
    @Builder.Default
    private Boolean signalAdjusted = false;

    @Column(name = "asked_at", nullable = false)
    private LocalDateTime askedAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    public void recordAnswer(String answerText, ReasonCategory reasonCategory, boolean signalAdjusted) {
        this.answerText = answerText;
        this.reasonCategory = reasonCategory;
        this.signalAdjusted = signalAdjusted;
        this.answeredAt = LocalDateTime.now();
    }
}
