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

    @Column(name = "year_month", nullable = false, columnDefinition = "CHAR(7)")
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

    public void addCurrentAmount(int amount) {
        this.currentAmount = (this.currentAmount == null ? 0 : this.currentAmount) + amount;
    }
}
