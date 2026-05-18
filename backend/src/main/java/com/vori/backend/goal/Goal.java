package com.vori.backend.goal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
