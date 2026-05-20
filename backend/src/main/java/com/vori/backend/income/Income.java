package com.vori.backend.income;

import com.vori.backend.common.PaymentMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 수입 1건. expenses 와 달리 신호등·스탯·EMA 같은 룰 없음, 단순 기록 + 통계용.
 * source ENUM 은 한국 대학생 맥락 (ALLOWANCE/PART_TIME/SCHOLARSHIP/...).
 */
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

    // 수금 수단. 미입력 가능. expenses 와 같은 ENUM 공유.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method",
        columnDefinition = "ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY')")
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('ALLOWANCE','PART_TIME','SCHOLARSHIP','SIDE_JOB','GIFT','INTEREST','OTHER')")
    private IncomeSource source;

    // 반복 수입 플래그 (월급·정기 알바 등)
    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private Boolean isRecurring = false;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false,
        columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // 행 수정 시 MySQL 이 자동 갱신. JPA 는 읽기 전용.
    @Column(name = "updated_at",
        columnDefinition = "DATETIME NULL ON UPDATE CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
