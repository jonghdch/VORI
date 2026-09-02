package com.vori.backend.goal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월 단위 절약 목표. 사용자가 "5월에 10만원 절약" / "5월에 카페 5만원 절약" 식으로 설정.
 * UNIQUE(user_id, year_month, category_id) — 단 category_id=NULL 중복은 앱(Service) 가 막아야 함 (MySQL UNIQUE+NULL 한계).
 * current_amount 갱신 규칙은 docs/domain.md 의 "지출 1건 처리 흐름" 참조.
 */
@Entity
@Table(name = "goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // NULL = 해당 월 전체 지출 대상 목표. 값 있음 = 특정 카테고리 목표
    @Column(name = "category_id")
    private Long categoryId;

    /**
     * 백틱 필수 — year_month 는 MySQL 예약어(INTERVAL ... YEAR_MONTH)다.
     * SELECT 는 Hibernate 가 별칭을 붙여(g1_0.year_month) 통과하지만, INSERT 의 컬럼 목록은
     * 별칭 없이 나가서 문법 오류가 난다. 백틱을 빼면 목표 생성이 500 으로 실패한다.
     */
    @Column(name = "`year_month`", nullable = false, columnDefinition = "CHAR(7)")
    private String yearMonth;

    @Column(name = "target_amount", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer targetAmount;

    @Column(name = "current_amount", columnDefinition = "INT UNSIGNED")
    @Builder.Default
    private Integer currentAmount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
            columnDefinition = "ENUM('ACTIVE','DONE','ABANDONED')")
    @Builder.Default
    private GoalStatus status = GoalStatus.ACTIVE;

    /**
     * 절약액을 누적하고, 목표치를 넘으면 DONE 으로 전이한다.
     * ExpenseService 는 ACTIVE 목표만 조회하므로 DONE 이 된 뒤에는 더 쌓이지 않는다.
     */
    public void addCurrentAmount(int amount) {
        this.currentAmount = (this.currentAmount == null ? 0 : this.currentAmount) + amount;
        if (this.status == GoalStatus.ACTIVE && this.currentAmount >= this.targetAmount) {
            this.status = GoalStatus.DONE;
            // TODO: 기획 확정 시 GOAL_ACHIEVED 보너스 스탯 부여 (docs/domain.md 에 TBD 로 남아 있음)
        }
    }

    /** 진행률(%). 100 을 넘지 않게 자른다. targetAmount 는 NOT NULL 이지만 0 방어. */
    public int progressPct() {
        if (targetAmount == null || targetAmount <= 0) return 0;
        int current = currentAmount == null ? 0 : currentAmount;
        return Math.min(100, (int) ((long) current * 100 / targetAmount));
    }

    /**
     * 목표 금액 수정. 바뀐 금액 기준으로 달성 여부를 다시 판정한다.
     *
     * 올려서 미달이 되면 DONE 을 ACTIVE 로 되돌린다 — 그러지 않으면 진행률은 55% 인데 상태만
     * DONE 인 모순이 남고, ExpenseService 가 ACTIVE 목표만 조회하므로 그 뒤로 영영 누적되지 않는다.
     * 포기(ABANDONED)한 목표는 금액을 고쳐도 되살리지 않는다.
     */
    public void updateTargetAmount(int amount) {
        this.targetAmount = amount;
        if (this.status == GoalStatus.ABANDONED) return;

        int current = this.currentAmount == null ? 0 : this.currentAmount;
        this.status = (current >= amount) ? GoalStatus.DONE : GoalStatus.ACTIVE;
    }

    public void abandon() {
        this.status = GoalStatus.ABANDONED;
    }
}
