package com.vori.backend.ledger;

import com.vori.backend.expense.Expense;
import com.vori.backend.expense.Signal;
import com.vori.backend.income.Income;

import java.time.LocalDate;

/**
 * 월별 가계부 한 줄 — 지출/수입 통합 표현.
 * - type=EXPENSE: category=카테고리명, signal=합리성 신호
 * - type=INCOME : category=수입 출처(IncomeSource enum), signal=null
 */
public record LedgerResponse(
        Long id,
        String type,
        LocalDate date,
        String item,
        int amount,
        String category,
        Signal signal
) {
    public static LedgerResponse expense(Expense e, String categoryName) {
        return new LedgerResponse(
                e.getId(),
                "EXPENSE",
                e.getSpentAt().toLocalDate(),
                e.getItem(),
                e.getAmount(),
                categoryName,
                e.getSignalFinal());
    }

    public static LedgerResponse income(Income i) {
        return new LedgerResponse(
                i.getId(),
                "INCOME",
                i.getReceivedAt(),
                i.getNote(),
                i.getAmount(),
                i.getSource() == null ? null : i.getSource().name(),
                null);
    }
}
