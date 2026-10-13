package com.vori.backend.paymentinstrument.dto;

import com.vori.backend.paymentinstrument.InstrumentKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/payment-instruments — 카드사·은행은 코드만 받는다(예: TOSSBANK). openingBalance 는 비우면 0. */
public record PaymentInstrumentCreateRequest(
        @NotNull(message = "종류를 선택해주세요.")
        InstrumentKind kind,

        @NotBlank(message = "카드사·은행을 선택해주세요.")
        @Pattern(regexp = "[A-Z_]{1,20}", message = "카드사·은행 코드가 올바르지 않습니다.")
        String issuer,

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 40, message = "이름은 최대 40자까지 입력 가능합니다.")
        String name,

        @Min(value = 0, message = "잔액은 0원 이상이어야 합니다.")
        @Max(value = 2_000_000_000, message = "잔액이 너무 큽니다.")
        Integer openingBalance
) {}
