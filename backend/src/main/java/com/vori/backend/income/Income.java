package com.vori.backend.income;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "incomes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt;

    @Column(nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('ALLOWANCE','PART_TIME','SCHOLARSHIP','SIDE_JOB','GIFT','INTEREST','OTHER')")
    private IncomeSource source;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false,
        columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
