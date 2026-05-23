package com.vori.backend.category.dto;

/**
 * POST /api/categories/categorize 요청 바디.
 * name = 사용자가 가계부 "내역" 필드에 입력한 텍스트.
 */
public record CategorizeRequest(String name) {}
