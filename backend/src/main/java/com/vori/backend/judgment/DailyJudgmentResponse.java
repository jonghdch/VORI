package com.vori.backend.judgment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        boolean alreadyJudged,
        // 판정 단계(docs/judgment-flow.md). PENDING 이면 위 보상은 "확정 때 줄 예정" 값이고 아직 지급 전이다.
        JudgmentStatus status,
        // 예외 지출 사유를 반영하기 전 1차 결과. 확정 화면이 「빨강 → 초록」처럼 달라진 점을 보여 준다. V50 이전 판정은 null·빈 값.
        Signal initialSignal,
        Map<StatType, GroupJudgment> initialGroupJudgments
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

    // GroupJudgment 칸이 나중에 바뀌어도 예전에 저장한 JSON 을 읽을 수 있게, 모르는 칸은 건너뛴다.
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DailyJudgmentResponse.class);

    /** 저장된 판정을 응답으로. V49 이전 행은 스탯별 결과가 없어 빈 값, 절약액은 코인 × 100 으로 어림한다. */
    static DailyJudgmentResponse from(DailyJudgment j, boolean alreadyJudged) {
        Map<StatType, Integer> rewards = new EnumMap<>(StatType.class);
        // 0 인 스탯은 저장하지 않으므로(reward_details 는 받은 스탯만) 네 칸을 0 으로 먼저 채운다
        for (StatType type : StatType.values()) rewards.put(type, 0);
        if (j.getRewardDetails() != null) for (String pair : j.getRewardDetails().split(",")) {
            String[] values = pair.split(":");
            if (values.length == 2) try { rewards.put(StatType.valueOf(values[0]), Integer.parseInt(values[1])); } catch (IllegalArgumentException ignored) {}
        }
        int saved = j.getSavedAmount() != null ? j.getSavedAmount() : j.getCoinReward() * 100;
        return new DailyJudgmentResponse(
                j.getJudgmentDate(), j.getSignal(), j.getExpenseCount(),
                j.getCoinReward(), j.getStatRewardPerType(), rewards, groupsFromJson(j.getGroupDetails()), saved, j.getJudgedAt(), alreadyJudged,
                j.getStatus(), j.getInitialSignal(), groupsFromJson(j.getInitialGroupDetails()));
    }

    /** 스탯별 판정 결과를 판정 행(group_details)에 넣을 JSON 으로. */
    static String groupsToJson(Map<StatType, GroupJudgment> groups) {
        try {
            return JSON.writeValueAsString(groups);
        } catch (Exception e) {
            throw new IllegalStateException("판정 결과를 저장할 형태로 바꾸지 못했어요", e);
        }
    }

    /** 저장된 JSON 을 스탯별 판정 결과로. 없거나 읽지 못하면 빈 값 — 화면은 「기록 없음」으로 보여 준다. */
    static Map<StatType, GroupJudgment> groupsFromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<StatType, GroupJudgment> parsed = JSON.readValue(json, new TypeReference<Map<StatType, GroupJudgment>>() {});
            return parsed == null || parsed.isEmpty() ? Map.of() : new EnumMap<>(parsed);
        } catch (Exception e) {
            log.warn("저장된 스탯별 판정을 읽지 못해 「기록 없음」으로 보여 준다 — {}", e.getMessage());
            return Map.of();
        }
    }
}
