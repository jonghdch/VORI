package com.vori.backend.category.dto;

/**
 * POST /api/categories/categorize 응답.
 * 매칭 실패 시 leafId=null (프론트는 "기타" 로 표시).
 */
public record CategorizeResponse(
        Long leafId,
        String leafName,
        Long parentId,
        String parentName,
        Double score
) {
    public static CategorizeResponse empty() {
        return new CategorizeResponse(null, null, null, null, null);
    }
}
