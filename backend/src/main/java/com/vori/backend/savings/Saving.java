package com.vori.backend.savings;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 저축 1건 단순 기록. 수익률·시세 추적 없음. AI 분석 없음.
 */
@Entity
@Table(name = "savings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Saving {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "saved_at", nullable = false)
    private LocalDate savedAt;

    @Column(nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer amount;

    @Column(nullable = false, length = 100)
    private String item;

    @Enumerated(EnumType.STRING)
    @Column(name = "saving_type", nullable = false,
        columnDefinition = "ENUM('DEPOSIT','INVEST')")
    private SavingType savingType;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false,
        columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at",
        columnDefinition = "DATETIME NULL ON UPDATE CURRENT_TIMESTAMP",
        insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
