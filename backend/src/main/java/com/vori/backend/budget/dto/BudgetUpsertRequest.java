package com.vori.backend.budget.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 월 예산 설정. 같은 달에 다시 보내면 금액만 바뀐다(upsert).
 * 월당 1건이라 생성·수정을 나눌 이유가 없다 — 프론트가 존재 여부를 먼저 조회할 필요도 없어진다.
 */
public record BudgetUpsertRequest(
        @NotBlank(message = "예산 월을 입력해주세요.")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "예산 월은 YYYY-MM 형식이어야 합니다.")
        String yearMonth,

        @NotNull(message = "예산 금액을 입력해주세요.")
        @Min(value = 1, message = "예산 금액은 1원 이상이어야 합니다.")
        Integer amount
) {}
