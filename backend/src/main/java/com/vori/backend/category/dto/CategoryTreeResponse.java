package com.vori.backend.category.dto;

import com.vori.backend.category.Category;
import com.vori.backend.common.StatType;

import java.util.List;

/**
 * 카테고리 트리 응답.
 * 대분류 1개 = CategoryTreeResponse 1개, children 에 상세 카테고리들이 들어감.
 * 상세 카테고리는 children 이 빈 리스트.
 */
public record CategoryTreeResponse(
    Long id,
    String name,
    StatType statType,
    Integer sortOrder,
    List<CategoryTreeResponse> children
) {
    public static CategoryTreeResponse parent(Category c, List<CategoryTreeResponse> children) {
        return new CategoryTreeResponse(
            c.getId(), c.getName(), c.getStatType(), c.getSortOrder(), children
        );
    }

    public static CategoryTreeResponse leaf(Category c) {
        return new CategoryTreeResponse(
            c.getId(), c.getName(), c.getStatType(), c.getSortOrder(), List.of()
        );
    }
}
