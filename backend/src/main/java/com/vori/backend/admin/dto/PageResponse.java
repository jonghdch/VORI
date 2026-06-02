package com.vori.backend.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지네이션 공통 응답. Spring Data 의 Page 내부 구조를 그대로 노출하지 않고
 * 프론트가 필요로 하는 필드만 추린 얇은 래퍼.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <E, T> PageResponse<T> of(Page<E> page, java.util.function.Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
