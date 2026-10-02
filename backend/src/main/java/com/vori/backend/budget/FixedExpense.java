package com.vori.backend.budget;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "fixed_expenses")
@Getter @Builder @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class FixedExpense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 50) private String name;
    @Column(nullable = false) private Integer amount;
}
