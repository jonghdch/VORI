package com.vori.backend.inquiry;

import com.vori.backend.common.StatType;
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

    /** 개별 지출이 아니라 하루 스탯 그룹의 예산 초과를 묻는 질문인지 여부. */
    @Column(name = "daily_group_inquiry", nullable = false)
    @Builder.Default
    private Boolean dailyGroupInquiry = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_stat_type", columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType groupStatType;

    @Column(name = "group_amount")
    private Integer groupAmount;

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

    /**
     * RED 판정 직후, 지출과 같은 트랜잭션에서 넣는 대기 질문. 문구는 템플릿이다.
     *
     * Gemini 가 만든 문구를 기다렸다가 넣으면 (1) 호출이 15초 재시도 끝에 실패한 지출은
     * 영영 질문이 없고 (2) 그 사이 화면은 빈 목록을 본다. 행을 먼저 만들어 두면 둘 다
     * 사라진다 — AI 문구는 나중에 {@link #refineQuestion} 으로 덮어쓴다.
     * 어투는 GeminiClient.generateQuestion 프롬프트(반려 펫이 말을 걸듯이)에 맞춘다.
     */
    public static AiInquiry pending(Long expenseId, Long userId, String item, Integer amount) {
        return AiInquiry.builder()
                .expenseId(expenseId)
                .userId(userId)
                .question(templateQuestion(item, amount))
                .signalAdjusted(false)
                .askedAt(LocalDateTime.now())
                .build();
    }

    /** 하루 예산을 초과한 스탯 그룹의 지출을 한 번에 묻는다. */
    public static AiInquiry pendingDailyGroup(Long expenseId, Long userId, StatType statType,
                                               String label, int totalAmount, int availableAmount) {
        return AiInquiry.builder()
                .expenseId(expenseId)
                .userId(userId)
                .dailyGroupInquiry(true)
                .groupStatType(statType)
                .groupAmount(totalAmount)
                .question(templateDailyGroupQuestion(label, totalAmount, availableAmount))
                .signalAdjusted(false)
                .askedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 조사(은/는)를 붙이지 않는 문형을 써서 내역이 무엇이든 어색하지 않게 한다.
     * 답변 화면에 사유 칩이 있어 「골라 주거나」를 넣었다 — 질문 문구 생성을 끄면(AiSwitches) 모두가 이 문구를 본다.
     */
    static String templateQuestion(String item, Integer amount) {
        String what = (item == null || item.isBlank()) ? "이번 지출" : "이번 " + item.trim();
        String won = amount == null ? "" : String.format(java.util.Locale.KOREA, " %,d원", amount);
        return what + won + ", 평소보다 큰 지출이었어. 어떤 일이었는지 골라 주거나 가볍게 적어 줄래?";
    }

    static String templateDailyGroupQuestion(String label, int totalAmount, int availableAmount) {
        return String.format(java.util.Locale.KOREA,
                "오늘 %s 지출이 합계 %,d원으로 사용할 수 있는 %,d원을 넘었어. 특별한 이유가 있었는지 골라 주거나 가볍게 적어 줄래?",
                label, totalAmount, availableAmount);
    }

    public void recordAnswer(String answerText, ReasonCategory reasonCategory, boolean signalAdjusted) {
        this.answerText = answerText;
        this.reasonCategory = reasonCategory;
        this.signalAdjusted = signalAdjusted;
        this.answeredAt = LocalDateTime.now();
    }
}
