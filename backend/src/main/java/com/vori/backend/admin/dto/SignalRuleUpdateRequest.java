package com.vori.backend.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 합리성 룰 수정 요청. z_green < z_red 불변식은 서비스에서 검증.
 */
public record SignalRuleUpdateRequest(
        @NotNull BigDecimal zRed,
        @NotNull BigDecimal zGreen
) {}
