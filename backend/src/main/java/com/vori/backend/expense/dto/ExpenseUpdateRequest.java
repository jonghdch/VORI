package com.vori.backend.expense.dto;

import com.vori.backend.common.PaymentMethod;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExpenseUpdateRequest(
        @NotBlank @Size(max = 100) String item,
        @NotNull @Min(1) Integer amount,
        @NotNull Long categoryId,
        @NotNull PaymentMethod paymentMethod
) {}
