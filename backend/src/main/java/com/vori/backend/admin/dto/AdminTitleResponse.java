package com.vori.backend.admin.dto;

import com.vori.backend.title.Title;
import com.vori.backend.title.TitleMetricType;

import java.time.LocalDateTime;

/**
 * 어드민 칭호 목록 한 행. 사용자용 TitleResponse 와 달리 비활성 칭호·보유자 수·
 * 해금하는 테마까지 실어 운영 판단(삭제 가능 여부 등)을 화면에서 할 수 있게 한다.
 */
public record AdminTitleResponse(
        Long id,
        String code,
        String name,
        String description,
        TitleMetricType metricType,
        long threshold,
        boolean enabled,
        int sortOrder,
        long holderCount,
        String unlocksThemeName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminTitleResponse of(Title t, long holderCount, String unlocksThemeName) {
        return new AdminTitleResponse(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getMetricType(), t.getThreshold(), Boolean.TRUE.equals(t.getEnabled()),
                t.getSortOrder() == null ? 0 : t.getSortOrder(),
                holderCount, unlocksThemeName, t.getCreatedAt(), t.getUpdatedAt());
    }
}
