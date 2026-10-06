package com.vori.backend.category.dto;

import java.util.List;

/**
 * POST /api/categories/categorize 응답.
 * 매칭 실패 시 leafId=null (프론트는 "기타" 로 표시).
 * source: 어디서 정했는지 — HISTORY(내 기록)·RULE(상호 규칙)·EMBEDDING(임베딩)·ASK(묻기 규칙의 기본값)·FALLBACK(기타 생활).
 * askType: 이름만으로 애매해 물어볼 때 WHAT(무엇을 샀나)·DINE_OR_DELIVERY(매장/배달), 아니면 null.
 * candidates: 물어볼 때의 칩. 첫 칩이 아니어도 leafId 와 같은 칩이 기본 선택이다. 묻지 않으면 빈 목록.
 */
public record CategorizeResponse(
        Long leafId,
        String leafName,
        Long parentId,
        String parentName,
        Double score,
        String source,
        String askType,
        List<Candidate> candidates
) {
    public record Candidate(Long leafId, String label, String leafName) {}

    public static CategorizeResponse empty() {
        return new CategorizeResponse(null, null, null, null, null, null, null, List.of());
    }
}
