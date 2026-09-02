package com.vori.backend.goal.dto;

import com.vori.backend.goal.Goal;

/**
 * 절약 목표 한 건.
 * categoryName 이 null 이면 그 달 전체 목표.
 * progressPct 는 서버가 계산해 내려준다 — 프론트에서 0 나누기를 신경 쓰지 않게.
 */
public record GoalResponse(
        Long id,
        String yearMonth,
        Long categoryId,
        String categoryName,
        int targetAmount,
        int currentAmount,
        int progressPct,
        String status
) {
    public static GoalResponse of(Goal g, String categoryName) {
        return new GoalResponse(
                g.getId(),
                g.getYearMonth(),
                g.getCategoryId(),
                categoryName,
                g.getTargetAmount() == null ? 0 : g.getTargetAmount(),
                g.getCurrentAmount() == null ? 0 : g.getCurrentAmount(),
                g.progressPct(),
                g.getStatus().name());
    }
}
