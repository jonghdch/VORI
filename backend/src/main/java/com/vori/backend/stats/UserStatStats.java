package com.vori.backend.stats;

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
 * 사용자별 스탯 4종 EMA 통계. 회원가입 시 4행 자동 INSERT (mean=0, stddev=0, sample_count=0).
 * 용도: ① 펫 스탯 점수 환산 (stat_delta 계산), ② 이례 감지 (z_score 산정·signal 판정).
 * 갱신 공식 (α, sample_count 등) 은 docs/domain.md 참조. Composite PK = (user_id, stat_type).
 */
@Entity
@Table(name = "user_stat_stats")
@IdClass(UserStatStatsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserStatStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "stat_type",
            columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType statType;

    // 건당 평균 지출액 (Exponential Moving Average). EMA 갱신은 Service 책임
    @Column(name = "mean_ema", nullable = false, precision = 12, scale = 2)
    private BigDecimal meanEma;

    // 건당 지출 표준편차 (EMA). 너무 작으면 z 계산 가드
    @Column(name = "stddev_ema", nullable = false, precision = 12, scale = 2)
    private BigDecimal stddevEma;

    // EMA 에 반영된 총 건수. N_MIN 미만이면 통계 불안정 → z 계산 스킵 (TBD)
    @Column(name = "sample_count", nullable = false)
    @Builder.Default
    private Integer sampleCount = 0;

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
            insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public void updateEma(BigDecimal newMeanEma, BigDecimal newStddevEma, int newSampleCount) {
        this.meanEma = newMeanEma;
        this.stddevEma = newStddevEma;
        this.sampleCount = newSampleCount;
    }
}
