package com.vori.backend.pet;

import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 펫 성장 이력. 절약·목표 달성·보너스로 stat 가 변할 때마다 1행 추가.
 * reason 으로 출처 구분 (EXPENSE_SAVING / GOAL_ACHIEVED / BONUS).
 * expense_id 또는 goal_id 중 하나가 채워짐 (reason 에 따라).
 */
@Entity
@Table(name = "pet_growth_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PetGrowthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false)
    private Long petId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "goal_id")
    private Long goalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stat_type", nullable = false,
        columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType statType;

    @Column(nullable = false)
    private Integer delta;

    @Column(name = "saved_amount", nullable = false)
    private Integer savedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('EXPENSE_SAVING','GOAL_ACHIEVED','BONUS')")
    private GrowthReason reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
