package com.vori.backend.income.dto;

import com.vori.backend.common.PaymentMethod;
import com.vori.backend.income.IncomeSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/incomes 요청.
 * 가계부 화면의 "수입" 행 하나에 대응.
 */
public record IncomeCreateRequest(
        @NotNull(message = "수입 카테고리를 선택해주세요.")
        IncomeSource source,

        @NotNull(message = "금액을 입력해주세요.")
        @Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
        Integer amount,

        @NotBlank(message = "수입 내역을 입력해주세요.")
        @Size(max = 100, message = "내역은 최대 100자까지 입력 가능합니다.")
        String item,

        LocalDate receivedAt,
        PaymentMethod paymentMethod,

        Boolean isRecurring
) {
    public IncomeCreateRequest {
        if (receivedAt == null) receivedAt = LocalDate.now();
        if (isRecurring == null) isRecurring = false;
    }
}
