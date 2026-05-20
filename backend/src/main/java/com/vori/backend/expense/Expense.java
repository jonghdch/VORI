package com.vori.backend.expense;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 지출 1건. VORI 의 핵심 테이블 — 입력되면 신호등·EMA·펫 성장이 트리거됨.
 * 계산식 (saved_amount / z_score / signal_initial / signal_final / stat_delta) 은 docs/domain.md 참조.
 */
@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 지출 시각. 사용자가 시각 미입력 시 YYYY-MM-DD 00:00:00 으로 저장 (time_provided=false 로 표시)
    @Column(name = "spent_at", nullable = false)
    private LocalDateTime spentAt;

    // TRUE = 사용자가 시각까지 입력, FALSE = 날짜만 (화면에서 00:00 숨김 처리용)
    @Column(name = "time_provided", nullable = false)
    @Builder.Default
    private Boolean timeProvided = false;

    @Column(nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer amount;

    // 결제 수단. 미입력 가능 (NULL).
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method",
        columnDefinition = "ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY')")
    private PaymentMethod paymentMethod;

    @Column(nullable = false, length = 100)
    private String item;

    // 사용자 자발적 메모. 입력해두면 AI 가 사유 안 물음 (이례 케이스라도)
    @Column(length = 200)
    private String memo;

    // 반복 결제 플래그 (통신비·구독 등). TRUE 면 신호등 산정 시 RED 자동 제외
    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private Boolean isRecurring = false;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    // categories.stat_type 캐시 — INSERT 시 부모 카테고리에서 복사. JOIN 없이 펫 성장·EMA 매칭용
    @Enumerated(EnumType.STRING)
    @Column(name = "stat_type", nullable = false,
            columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType statType;

    // 평소 분포 대비 이례도 (표준편차 단위). user_stat_stats 기준 산출
    @Column(name = "z_score", precision = 6, scale = 3)
    private BigDecimal zScore;

    // z-score 기반 1차 판정. 임계값은 SignalConfig (TBD)
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_initial",
            columnDefinition = "ENUM('RED','GRAY','GREEN')")
    private Signal signalInitial;

    // AI 사유 받아서 보정된 최종 판정. AI 질문 안 했으면 signal_initial 과 동일
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_final",
            columnDefinition = "ENUM('RED','GRAY','GREEN')")
    private Signal signalFinal;

    // 펫 성장량. floor(max(saved_amount, 0) / 1000). 절약 시에만 양수
    @Column(name = "stat_delta", columnDefinition = "INT UNSIGNED")
    @Builder.Default
    private Integer statDelta = 0;

    // mean_ema - amount. 양수면 절약, 음수면 과지출
    @Column(name = "saved_amount")
    private Integer savedAmount;

    @Column(name = "created_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP",
            insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // 행 수정 시 MySQL 이 자동 갱신. JPA 는 읽기 전용.
    @Column(name = "updated_at",
        columnDefinition = "DATETIME NULL ON UPDATE CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public void updateCalculations(BigDecimal zScore, Signal signal, Integer savedAmount, Integer statDelta) {
        this.zScore = zScore;
        this.signalInitial = signal;
        this.signalFinal = signal;
        this.savedAmount = savedAmount;
        this.statDelta = statDelta;
    }

    public void updateSignalFinal(Signal signalFinal) {
        this.signalFinal = signalFinal;
    }
}
