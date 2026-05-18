package com.vori.backend.expense;

import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = "spent_at", nullable = false)
    private LocalDateTime spentAt;

    @Column(name = "time_provided", nullable = false)
    @Builder.Default
    private Boolean timeProvided = false;

    @Column(nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer amount;

    @Column(nullable = false, length = 100)
    private String item;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stat_type", nullable = false,
        columnDefinition = "ENUM('ENERGY','CHARM','IQ','ENDURANCE')")
    private StatType statType;

    @Column(name = "z_score", precision = 6, scale = 3)
    private BigDecimal zScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_initial",
        columnDefinition = "ENUM('RED','GRAY','GREEN')")
    private Signal signalInitial;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_final",
        columnDefinition = "ENUM('RED','GRAY','GREEN')")
    private Signal signalFinal;

    @Column(name = "stat_delta", columnDefinition = "INT UNSIGNED")
    @Builder.Default
    private Integer statDelta = 0;

    @Column(name = "saved_amount")
    private Integer savedAmount;

    @Column(name = "created_at", nullable = false,
        columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
