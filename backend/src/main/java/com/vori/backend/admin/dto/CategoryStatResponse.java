package com.vori.backend.admin.dto;

/**
 * 어드민 지출 카테고리 통계 한 행. 전체 사용자 기준 집계.
 */
public record CategoryStatResponse(
        Long categoryId,
        String categoryName,
        long count,
        long totalAmount,
        long avgAmount,
        long redCount,
        long grayCount,
        long greenCount
) {}
