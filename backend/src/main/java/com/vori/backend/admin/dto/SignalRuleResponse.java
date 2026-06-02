package com.vori.backend.admin.dto;

import com.vori.backend.expense.SignalConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 합리성 신호 판정 룰. z_green 이하=GREEN, z_red 초과=RED, 사이=GRAY.
 */
public record SignalRuleResponse(
        BigDecimal zRed,
        BigDecimal zGreen,
        LocalDateTime updatedAt
) {
    public static SignalRuleResponse from(SignalConfig c) {
        return new SignalRuleResponse(c.getZRed(), c.getZGreen(), c.getUpdatedAt());
    }
}
