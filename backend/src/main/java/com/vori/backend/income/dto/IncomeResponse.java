package com.vori.backend.income.dto;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.income.Income;
import com.vori.backend.income.IncomeSource;

import java.time.LocalDate;

public record IncomeResponse(
        Long id,
        LocalDate receivedAt,
        Integer amount,
        // Income 엔티티 'note' 필드를 가계부 화면의 "내역(item)" 으로 그대로 사용
        String item,
        IncomeSource source,
        PaymentMethod paymentMethod,
        Boolean isRecurring
) {
    public static IncomeResponse from(Income i) {
        return new IncomeResponse(
                i.getId(),
                i.getReceivedAt(),
                i.getAmount(),
                i.getNote(),
                i.getSource(),
                i.getPaymentMethod(),
                i.getIsRecurring()
        );
    }
}
