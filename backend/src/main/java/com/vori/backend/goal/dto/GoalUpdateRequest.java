package com.vori.backend.goal.dto;

import jakarta.validation.constraints.Min;

/**
 * 목표 수정 요청. 둘 다 선택 — 넘긴 값만 반영한다.
 * abandon=true 면 목표를 포기(ABANDONED) 처리한다. 되돌리기는 지원하지 않는다.
 */
public record GoalUpdateRequest(
        @Min(value = 1, message = "목표 금액은 1원 이상이어야 합니다.")
        Integer targetAmount,

        Boolean abandon
) {}
