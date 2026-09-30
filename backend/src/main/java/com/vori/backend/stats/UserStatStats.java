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

    /**
     * 평균·편차가 온보딩 설문으로 채운 초기값(BaselineSeeder)인지. 실제 지출이 한 번이라도 반영되면 false.
     * 표본 수만으로는 "씨딩값" 과 "실제 N_MIN 건" 을 구분할 수 없어서 따로 표시한다.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean seeded = false;

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
            insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /** 실제 지출 반영. 씨딩값 위에 쌓이면 그때부터는 실제 기록이다. */
    public void updateEma(BigDecimal newMeanEma, BigDecimal newStddevEma, int newSampleCount) {
        this.meanEma = newMeanEma;
        this.stddevEma = newStddevEma;
        this.sampleCount = newSampleCount;
        this.seeded = false;
    }

    /** 온보딩 초기 기준선(BaselineSeeder 전용). 실제 지출이 반영되기 전까지는 다시 씨딩할 수 있다. */
    public void seedBaseline(BigDecimal mean, BigDecimal stddev, int sampleCount) {
        this.meanEma = mean;
        this.stddevEma = stddev;
        this.sampleCount = sampleCount;
        this.seeded = true;
    }
}
