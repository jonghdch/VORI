package com.vori.backend.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReceiptOcrJobRepository extends JpaRepository<ReceiptOcrJob, Long> {

    List<ReceiptOcrJob> findByUserIdOrderByRequestedAtDesc(Long userId);

    List<ReceiptOcrJob> findByExpenseId(Long expenseId);

    List<ReceiptOcrJob> findByStatus(OcrStatus status);

    /**
     * 실제로 값을 읽어 낸 영수증 수 — 영수증 칭호("스캔 마스터") 조건.
     *
     * 상태가 SUCCESS 여도 영수증이 아니거나 판독이 안 된 사진은 값이 모두 비어 있다(호출 자체는 성공).
     * 예전엔 상태만 봐서 이런 사진도 세어 아무 사진이나 올려 칭호를 딸 수 있었다.
     * 화면이 "인식 성공" 으로 보는 기준(금액이나 항목 중 하나라도 있음)과 같게 센다.
     * 항목은 빈 문자열도 "없음" 으로 본다 — 모델이 null 대신 "" 를 줄 때가 있고, 화면도 `!item` 으로
     * 빈 문자열을 실패로 처리한다. ReceiptService 의 trim() 은 공백만 있는 값을 null 로 바꾸지 않는다.
     */
    @Query("""
        SELECT COUNT(j) FROM ReceiptOcrJob j
        WHERE j.userId = :userId
          AND j.status = com.vori.backend.receipt.OcrStatus.SUCCESS
          AND (j.extractedAmount IS NOT NULL
               OR (j.extractedItem IS NOT NULL AND TRIM(j.extractedItem) <> ''))
        """)
    long countReadScans(@Param("userId") Long userId);
}
