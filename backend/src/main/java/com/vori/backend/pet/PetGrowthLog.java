package com.vori.backend.pet;

import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
