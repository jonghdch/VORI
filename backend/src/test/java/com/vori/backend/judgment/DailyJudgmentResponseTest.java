package com.vori.backend.judgment;

import com.vori.backend.common.StatType;
import com.vori.backend.expense.Signal;
import com.vori.backend.judgment.DailyJudgmentResponse.GroupJudgment;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 판정 행에 저장하는 스탯별 결과(JSON) 변환 — 저장한 그대로 돌아오고, 이상한 값에는 빈 값으로 물러선다. */
class DailyJudgmentResponseTest {

    @Test
    void 저장한_스탯별_결과가_그대로_돌아온다() {
        Map<StatType, GroupJudgment> groups = new EnumMap<>(StatType.class);
        groups.put(StatType.ENERGY, new GroupJudgment(247_272, 7_976, 9_708, 7_500, 16_000, 2_208, 1_104, false, Signal.GREEN));
        groups.put(StatType.CHARM, new GroupJudgment(154_545, 4_985, 0, 129_000, 218_000, 0, 0, false, Signal.RED));

        assertEquals(groups, DailyJudgmentResponse.groupsFromJson(DailyJudgmentResponse.groupsToJson(groups)));
    }

    @Test
    void 비었거나_깨진_값은_빈_결과로_읽는다() {
        for (String json : new String[]{null, "", "   ", "null", "{\"ENERGY\":", "[]"}) {
            assertTrue(DailyJudgmentResponse.groupsFromJson(json).isEmpty(), "입력: " + json);
        }
    }

    @Test
    void 모르는_칸이_섞여도_아는_칸은_읽는다() {
        // 나중에 GroupJudgment 칸이 바뀌어도 예전에 저장한 기록이 「기록 없음」으로 사라지지 않아야 한다
        String json = "{\"IQ\":{\"monthlyBudget\":123636,\"dailyBase\":3988,\"availableToday\":6979,\"todaySpent\":0,"
                + "\"monthSpent\":0,\"savedAmount\":6979,\"nextDayCarry\":3489,\"budgetExhausted\":false,\"signal\":\"GREEN\","
                + "\"removedLater\":1}}";

        GroupJudgment iq = DailyJudgmentResponse.groupsFromJson(json).get(StatType.IQ);

        assertNotNull(iq);
        assertEquals(6_979, iq.savedAmount());
        assertEquals(Signal.GREEN, iq.signal());
    }
}
