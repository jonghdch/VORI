package com.vori.backend.receipt;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipt_ocr_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReceiptOcrJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "receipt_path", nullable = false, length = 255)
    private String receiptPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('GOOGLE_VISION') DEFAULT 'GOOGLE_VISION'")
    @Builder.Default
    private OcrProvider provider = OcrProvider.GOOGLE_VISION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('PENDING','PROCESSING','SUCCESS','FAILED') DEFAULT 'PENDING'")
    @Builder.Default
    private OcrStatus status = OcrStatus.PENDING;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "extracted_amount", columnDefinition = "INT UNSIGNED")
    private Integer extractedAmount;

    @Column(name = "extracted_date")
    private LocalDate extractedDate;

    @Column(name = "extracted_item", length = 100)
    private String extractedItem;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
