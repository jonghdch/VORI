package com.vori.backend.stats.dto;

/**
 * 홈 대시보드 스탯 위젯용. 4 stat 의 누적 변동 + 총 절약액.
 */
public record StatsResponse(
        int energy,
        int charm,
        int iq,
        int endurance,
        int totalSaved
) {}
