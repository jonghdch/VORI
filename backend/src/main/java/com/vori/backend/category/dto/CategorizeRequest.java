package com.vori.backend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/categories/categorize 요청 바디.
 * name = 사용자가 가계부 "내역" 필드에 입력한 텍스트.
 * 길이 제한 = expenses.item 과 동일(100자) — 무제한 텍스트가 Gemini 로 직행하는 것 차단.
 */
public record CategorizeRequest(
        @NotBlank @Size(max = 100) String name
) {}
