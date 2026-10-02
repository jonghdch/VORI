package com.vori.backend.budget;

import com.vori.backend.common.StatType;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "user_stat_budgets")
@Getter @Builder @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class UserStatBudget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "`year_month`", nullable = false, columnDefinition = "CHAR(7)") private String yearMonth;
    @Enumerated(EnumType.STRING) @Column(name = "stat_type", nullable = false) private StatType statType;
    @Column(nullable = false) private Integer amount;
    public void updateAmount(int amount) { this.amount = amount; }
}
