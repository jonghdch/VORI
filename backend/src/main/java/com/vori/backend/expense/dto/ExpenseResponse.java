package com.vori.backend.expense.dto;

import com.vori.backend.common.StatType;
import com.vori.backend.expense.Expense;
import com.vori.backend.expense.Signal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseResponse(
        Long id,
        LocalDateTime spentAt,
        Boolean timeProvided,
        Integer amount,
        String item,
        Long categoryId,
        StatType statType,
        BigDecimal zScore,
        Signal signalInitial,
        Signal signalFinal,
        Integer savedAmount,
        Integer statDelta
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getSpentAt(),
                e.getTimeProvided(),
                e.getAmount(),
                e.getItem(),
                e.getCategoryId(),
                e.getStatType(),
                e.getZScore(),
                e.getSignalInitial(),
                e.getSignalFinal(),
                e.getSavedAmount(),
                e.getStatDelta()
        );
    }
}
