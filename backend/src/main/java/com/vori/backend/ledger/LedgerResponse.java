package com.vori.backend.ledger;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.Signal;
import com.vori.backend.income.Income;

import java.time.LocalDate;

/**
 * 월별 가계부 한 줄 — 지출/수입 통합 표현.
 * - type=EXPENSE: category=카테고리명, signal=합리성 신호, memo=지출 메모
 * - type=INCOME : category=수입 출처(IncomeSource enum), signal=null, memo=null
 * paymentMethod 는 PaymentMethod enum 이름(CASH/DEBIT/CREDIT/TRANSFER/MOBILE_PAY), 미입력 시 null.
 * aiJudged: 예외적 지출로 AI 질문(판정)을 거쳤는지. true 인 지출에만 "AI 판정" 배지 노출. (수입은 항상 false)
 * reason: AI 질문에 사용자가 답한 소비 사유(answerText). 질문이 없거나 미답변이면 null.
 */
public record LedgerResponse(
        Long id,
        String type,
        LocalDate date,
        String item,
        int amount,
        String category,
        Signal signal,
        String paymentMethod,
        String memo,
        boolean aiJudged,
        String reason
) {
    public static LedgerResponse expense(Expense e, String categoryName, boolean aiJudged, String reason) {
        return new LedgerResponse(
                e.getId(),
                "EXPENSE",
                e.getSpentAt().toLocalDate(),
                e.getItem(),
                e.getAmount(),
                categoryName,
                e.getSignalFinal(),
                e.getPaymentMethod() == null ? null : e.getPaymentMethod().name(),
                e.getMemo(),
                aiJudged,
                reason);
    }

    public static LedgerResponse income(Income i) {
        return new LedgerResponse(
                i.getId(),
                "INCOME",
                i.getReceivedAt(),
                i.getNote(),
                i.getAmount(),
                i.getSource() == null ? null : i.getSource().name(),
                null,
                i.getPaymentMethod() == null ? null : i.getPaymentMethod().name(),
                null,
                false,
                null);
    }
}
