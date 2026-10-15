package com.vori.backend.pettitle.dto;

import java.time.LocalDateTime;

/**
 * 칭호 탭의 과제 1개. 딴 것과 못 딴 것이 같은 모양으로 내려온다.
 * awardId·acquiredAt 은 딴 칭호만 값이 있고, equipped 는 지금 장착한 칭호인지.
 */
public record PetTitleItem(
        String code,
        String name,
        String description,
        String metricType,
        boolean hidden,
        long current,
        long threshold,
        int progressPct,
        boolean acquired,
        Long awardId,
        LocalDateTime acquiredAt,
        boolean equipped
) {}
