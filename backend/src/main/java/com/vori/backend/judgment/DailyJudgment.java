package com.vori.backend.judgment;

import com.vori.backend.expense.Signal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_judgments", uniqueConstraints =
        @UniqueConstraint(name = "uk_daily_judgment_user_date", columnNames = {"user_id", "judgment_date"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DailyJudgment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "judgment_date", nullable = false)
    private LocalDate judgmentDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "result_signal", nullable = false,
            columnDefinition = "ENUM('RED','GRAY','GREEN')")
    private Signal signal;
    @Column(name = "expense_count", nullable = false)
    private Integer expenseCount;
    @Column(name = "coin_reward", nullable = false)
    private Integer coinReward;
    @Column(name = "stat_reward_per_type", nullable = false)
    private Integer statRewardPerType;
    @Column(name = "reward_details", length = 255)
    private String rewardDetails;
    @Column(name = "judged_at", nullable = false)
    private LocalDateTime judgedAt;
    /** 스탯별 판정 결과(JSON) — 판정을 다시 열 때 판정한 순간과 같은 칸을 보여 주려고 저장한다. V49 이전 행은 null. */
    @Column(name = "group_details", columnDefinition = "TEXT")
    private String groupDetails;
    /** 실제로 남긴 금액(원). 코인은 이 값 / 100 이라 코인만으로는 100원 미만이 사라진다. V49 이전 행은 null. */
    @Column(name = "saved_amount")
    private Integer savedAmount;

    /** 판정 단계. 기존 행(V50 이전)은 이미 보상을 받은 판정이라 FINALIZED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "ENUM('PENDING','FINALIZED')")
    @Builder.Default
    private JudgmentStatus status = JudgmentStatus.FINALIZED;
    /** 1차 판정 신호 — 예외 지출 사유를 반영하기 전. V50 이전 행은 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "initial_signal", columnDefinition = "ENUM('RED','GRAY','GREEN')")
    private Signal initialSignal;
    /** 1차 스탯별 결과(JSON, group_details 와 같은 형식). V50 이전 행은 null. */
    @Column(name = "initial_group_details", columnDefinition = "TEXT")
    private String initialGroupDetails;
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;
    /** 보상 지급 시각 — 그날 자정에 한 번(JudgmentRewardSettler). null 이면 아직 지급 전. V50 이전 행은 판정 시각. */
    @Column(name = "rewarded_at")
    private LocalDateTime rewardedAt;

    public boolean isRewarded() {
        return rewardedAt != null;
    }

    public void markRewarded(LocalDateTime rewardedAt) {
        this.rewardedAt = rewardedAt;
    }

    public boolean isFinalized() {
        return status == JudgmentStatus.FINALIZED;
    }

    /** 확정 — 예외 지출 사유를 반영해 다시 계산한 최종 결과와 보상(자정에 지급할 값)을 담는다. */
    public void finalizeWith(Signal signal, int expenseCount, int coinReward, int statRewardPerType,
                             String rewardDetails, String groupDetails, int savedAmount, LocalDateTime finalizedAt) {
        refresh(signal, expenseCount, coinReward, statRewardPerType, rewardDetails, groupDetails, savedAmount, judgedAt);
        this.status = JudgmentStatus.FINALIZED;
        this.finalizedAt = finalizedAt;
    }

    /**
     * 관리자가 확정된 날을 다시 판정할 때 — 판정 결과만 최신 계산으로 바꾸고 보상 칸(코인·스탯·절약액)은 그대로 둔다.
     * 다시 판정해도 보상은 다시 주지 않으므로, 보상 칸을 새 계산으로 덮으면 실제로 받은 것과 화면이 달라진다.
     */
    public void refreshResult(Signal signal, int expenseCount, String groupDetails, LocalDateTime judgedAt) {
        this.signal = signal;
        this.expenseCount = expenseCount;
        this.groupDetails = groupDetails;
        this.judgedAt = judgedAt;
    }

    /**
     * 자정이 지나도록 사유 입력을 끝내지 않은 판정을 1차 판정 내용 그대로 확정한다(docs/judgment-flow.md D7).
     * PENDING 동안 보상 칸에는 1차 판정의 보상 값이 들어 있으므로 그대로 두고, 신호·스탯별 결과만 1차 값으로 맞춘다.
     */
    public void finalizeAsInitial(LocalDateTime finalizedAt) {
        if (initialSignal != null) this.signal = initialSignal;
        if (initialGroupDetails != null) this.groupDetails = initialGroupDetails;
        this.status = JudgmentStatus.FINALIZED;
        this.finalizedAt = finalizedAt;
    }

    /** 관리자 시연 재판정은 같은 날짜 행을 갱신해 결과 화면도 최신 계산을 보게 한다. */
    public void refresh(Signal signal, int expenseCount, int coinReward, int statRewardPerType,
                        String rewardDetails, String groupDetails, int savedAmount, LocalDateTime judgedAt) {
        this.signal = signal;
        this.expenseCount = expenseCount;
        this.coinReward = coinReward;
        this.statRewardPerType = statRewardPerType;
        this.rewardDetails = rewardDetails;
        this.groupDetails = groupDetails;
        this.savedAmount = savedAmount;
        this.judgedAt = judgedAt;
    }
}
