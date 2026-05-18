package com.vori.backend.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptOcrJobRepository extends JpaRepository<ReceiptOcrJob, Long> {

    List<ReceiptOcrJob> findByUserIdOrderByRequestedAtDesc(Long userId);

    List<ReceiptOcrJob> findByExpenseId(Long expenseId);

    List<ReceiptOcrJob> findByStatus(OcrStatus status);
}
