package com.vori.backend.goal.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 절약 목표 생성 요청.
 * categoryId 가 null 이면 그 달 전체를 대상으로 하는 목표.
 */
public record GoalCreateRequest(
        @NotBlank(message = "목표 월을 입력해주세요.")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "목표 월은 YYYY-MM 형식이어야 합니다.")
        String yearMonth,

        // 특정 카테고리 목표면 값 지정, 전체 목표면 생략
        Long categoryId,

        @NotNull(message = "목표 금액을 입력해주세요.")
        @Min(value = 1, message = "목표 금액은 1원 이상이어야 합니다.")
        Integer targetAmount
) {}
