package com.vori.backend.judgment;

import com.vori.backend.expense.Signal;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyJudgmentResponse(
        LocalDate date,
        Signal signal,
        int expenseCount,
        int coinReward,
        int statRewardPerType,
        LocalDateTime judgedAt,
        boolean alreadyJudged
) {
    static DailyJudgmentResponse from(DailyJudgment j, boolean alreadyJudged) {
        return new DailyJudgmentResponse(
                j.getJudgmentDate(), j.getSignal(), j.getExpenseCount(),
                j.getCoinReward(), j.getStatRewardPerType(), j.getJudgedAt(), alreadyJudged);
    }
}
