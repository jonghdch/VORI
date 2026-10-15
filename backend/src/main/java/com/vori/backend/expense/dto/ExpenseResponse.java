package com.vori.backend.expense.dto;

import com.vori.backend.common.PaymentMethod;
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
        PaymentMethod paymentMethod,
        StatType statType,
        BigDecimal zScore,
        Signal signalInitial,
        Signal signalFinal,
        Integer savedAmount,
        Integer statDelta,
        Boolean isRecurring,
        String memo,
        com.vori.backend.inquiry.ReasonCategory reasonCategory,
        Long paymentInstrumentId
) {
    /** 같은 응답에 카드·계좌 id 만 바꾼 것 — 저장 뒤 카드를 붙일 때 사유(reasonCategory)를 잃지 않으려고. */
    public ExpenseResponse withPaymentInstrumentId(Long instrumentId) {
        return new ExpenseResponse(id, spentAt, timeProvided, amount, item, categoryId, paymentMethod, statType,
                zScore, signalInitial, signalFinal, savedAmount, statDelta, isRecurring, memo, reasonCategory,
                instrumentId);
    }

    public static ExpenseResponse from(Expense e) {
        return from(e, null);
    }

    public static ExpenseResponse from(Expense e, com.vori.backend.inquiry.ReasonCategory reason) {
        return new ExpenseResponse(
                e.getId(),
                e.getSpentAt(),
                e.getTimeProvided(),
                e.getAmount(),
                e.getItem(),
                e.getCategoryId(),
                e.getPaymentMethod(),
                e.getStatType(),
                e.getZScore(),
                e.getSignalInitial(),
                e.getSignalFinal(),
                e.getSavedAmount(),
                e.getStatDelta(),
                e.getIsRecurring(),
                e.getMemo(),
                reason,
                e.getPaymentInstrumentId()
        );
    }
}
