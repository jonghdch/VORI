package com.vori.backend.judgment;

import com.vori.backend.expense.Signal;
import com.vori.backend.common.StatType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public record DailyJudgmentResponse(
        LocalDate date,
        Signal signal,
        int expenseCount,
        int coinReward,
        int statRewardPerType,
        Map<StatType, Integer> statRewards,
        Map<StatType, GroupJudgment> groupJudgments,
        int savedAmount,
        LocalDateTime judgedAt,
        boolean alreadyJudged
) {
    public record GroupJudgment(
            int monthlyBudget,
            int dailyBase,
            int availableToday,
            int todaySpent,
            int monthSpent,
            int savedAmount,
            int nextDayCarry,
            boolean budgetExhausted,
            Signal signal
    ) {}

    static DailyJudgmentResponse from(DailyJudgment j, boolean alreadyJudged) {
        Map<StatType, Integer> rewards = new EnumMap<>(StatType.class);
        if (j.getRewardDetails() != null) for (String pair : j.getRewardDetails().split(",")) {
            String[] values = pair.split(":");
            if (values.length == 2) try { rewards.put(StatType.valueOf(values[0]), Integer.parseInt(values[1])); } catch (IllegalArgumentException ignored) {}
        }
        return new DailyJudgmentResponse(
                j.getJudgmentDate(), j.getSignal(), j.getExpenseCount(),
                j.getCoinReward(), j.getStatRewardPerType(), rewards, Map.of(), j.getCoinReward() * 100, j.getJudgedAt(), alreadyJudged);
    }
}
