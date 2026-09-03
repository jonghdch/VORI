package com.vori.backend.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptOcrJobRepository extends JpaRepository<ReceiptOcrJob, Long> {

    List<ReceiptOcrJob> findByUserIdOrderByRequestedAtDesc(Long userId);

    List<ReceiptOcrJob> findByExpenseId(Long expenseId);

    List<ReceiptOcrJob> findByStatus(OcrStatus status);

    /** 인식에 성공한 영수증 수 — 영수증 칭호 조건. 실패 건은 세지 않는다. */
    long countByUserIdAndStatus(Long userId, OcrStatus status);
}
