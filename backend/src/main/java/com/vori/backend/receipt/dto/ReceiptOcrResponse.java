package com.vori.backend.receipt.dto;

import com.vori.backend.receipt.OcrStatus;
import com.vori.backend.receipt.ReceiptOcrJob;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * OCR 처리 결과.
 *
 * status=SUCCESS 여도 개별 값은 null 일 수 있다 — 영수증이 흐려 일부만 읽힌 경우다.
 * 화면은 읽힌 값만 채워 넣고 나머지는 사용자가 입력하게 하면 된다.
 * extracted 는 성공했을 때만 채워지고, 실패 시엔 errorMessage 를 보면 된다.
 */
public record ReceiptOcrResponse(
        Long id,
        OcrStatus status,
        Integer amount,
        LocalDate date,
        String item,
        ExtractedReceipt extracted,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        Long expenseId
) {
    public static ReceiptOcrResponse of(ReceiptOcrJob job, ExtractedReceipt extracted) {
        return new ReceiptOcrResponse(
                job.getId(),
                job.getStatus(),
                job.getExtractedAmount(),
                job.getExtractedDate(),
                job.getExtractedItem(),
                extracted,
                job.getErrorMessage(),
                job.getRequestedAt(),
                job.getCompletedAt(),
                job.getExpenseId());
    }

    /** 목록 조회용 — 원문 JSON 을 다시 파싱하지 않고 요약 필드만 내려준다. */
    public static ReceiptOcrResponse summary(ReceiptOcrJob job) {
        return of(job, null);
    }
}
