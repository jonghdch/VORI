package com.vori.backend.category.dto;

/**
 * POST /api/categories/categorize 응답.
 * 매칭 실패 시 leafId=null (프론트는 "기타" 로 표시).
 * source: 어디서 정했는지 — HISTORY(내 기록)·RULE(상호 규칙)·EMBEDDING(임베딩)·FALLBACK(기타 생활).
 */
public record CategorizeResponse(
        Long leafId,
        String leafName,
        Long parentId,
        String parentName,
        Double score,
        String source
) {
    public static CategorizeResponse empty() {
        return new CategorizeResponse(null, null, null, null, null, null);
    }
}
