package com.vori.backend.receipt;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 영수증 OCR 작업. 업로드된 사진을 Gemini 에 넘겨 상호·날짜·금액·품목을 뽑아낸 이력.
 *
 * 이미지는 보관하지 않는다(V8) — 가계부에 필요한 건 추출된 데이터이고, 영수증에는
 * 카드번호 뒷자리 같은 정보가 남아 있다. 그래서 receipt_path 는 항상 null 이고,
 * 추출 원문(JSON)을 extracted_text 에 남겨 재확인만 가능하게 한다.
 *
 * 처리는 동기다. 실측 응답이 5~26초라 사용자가 결과를 보고 바로 지출로 이어갈 수 있고,
 * 비동기로 하면 프론트에 폴링 화면이 따로 필요해진다.
 */
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

    // NULL = OCR 결과만 있고 아직 expenses 와 연결 안 됨. 사용자 확인 후 연결
    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 이미지를 보관하지 않으므로 항상 null. 컬럼은 과거 설계 호환을 위해 남겨둔다.
    @Column(name = "receipt_path", length = 255)
    private String receiptPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,
        columnDefinition = "ENUM('GOOGLE_VISION','GEMINI') DEFAULT 'GEMINI'")
    @Builder.Default
    private OcrProvider provider = OcrProvider.GEMINI;

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

    public void markProcessing() {
        this.status = OcrStatus.PROCESSING;
    }

    /** 추출 성공. rawJson 은 나중에 값을 다시 확인할 수 있게 원문 그대로 남긴다. */
    public void markSuccess(String rawJson, Integer amount, LocalDate date,
                            String item, LocalDateTime at) {
        this.status = OcrStatus.SUCCESS;
        this.extractedText = rawJson;
        this.extractedAmount = amount;
        this.extractedDate = date;
        this.extractedItem = item;
        this.completedAt = at;
    }

    /** 실패해도 행은 남긴다 — 어떤 이미지가 왜 실패했는지 추적할 수 있어야 한다. */
    public void markFailed(String message, LocalDateTime at) {
        this.status = OcrStatus.FAILED;
        this.errorMessage = message;
        this.completedAt = at;
    }

    /** 사용자가 확인 후 실제 지출로 등록했을 때 연결. */
    public void linkExpense(Long expenseId) {
        this.expenseId = expenseId;
    }
}
