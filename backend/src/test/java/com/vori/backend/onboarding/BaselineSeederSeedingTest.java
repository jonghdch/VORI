package com.vori.backend.onboarding;

import com.vori.backend.common.StatType;
import com.vori.backend.expense.SignalConfig;
import com.vori.backend.expense.SignalConfigService;
import com.vori.backend.stats.UserStatStats;
import com.vori.backend.stats.UserStatStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 씨딩은 "표본이 없거나, 씨딩값이 아직 그대로일 때" 만 한다 — 추측이 아니라 seeded 표시로 판단한다.
 * 재현: 실제 쇼핑 3만원 5건인 사용자가 설문을 내면 표본 수 5 가 씨딩 흔적과 구분되지 않아
 * 실제 평균이 설문 유도값(5만)으로 덮어써졌다.
 */
class BaselineSeederSeedingTest {

    private final UserStatStatsRepository statsRepository = mock(UserStatStatsRepository.class);
    private final SignalConfigService signalConfigService = mock(SignalConfigService.class);
    private final BaselineSeeder seeder = new BaselineSeeder(statsRepository, signalConfigService);
    private final Map<StatType, UserStatStats> stats = new EnumMap<>(StatType.class);

    @BeforeEach
    void setUp() {
        when(signalConfigService.getConfig()).thenReturn(SignalConfig.builder().zRed(new BigDecimal("2.00")).build());
        for (StatType t : StatType.values()) put(t, UserStatStats.builder().userId(1L).statType(t)
                .meanEma(BigDecimal.ZERO).stddevEma(BigDecimal.ZERO).sampleCount(0).build());
    }

    private UserStatStats put(StatType t, UserStatStats s) {
        stats.put(t, s);
        when(statsRepository.findByUserIdAndStatType(1L, t)).thenReturn(Optional.of(s));
        return s;
    }

    /** 실제 지출이 반영된 기록 — updateEma 로 만든다. */
    private UserStatStats real(StatType t, int count, String mean) {
        UserStatStats s = put(t, UserStatStats.builder().userId(1L).statType(t)
                .meanEma(BigDecimal.ZERO).stddevEma(BigDecimal.ZERO).sampleCount(0).build());
        s.updateEma(new BigDecimal(mean), BigDecimal.ZERO, count);
        return s;
    }

    private static void assertMean(String expected, UserStatStats s) {
        assertEquals(0, new BigDecimal(expected).compareTo(s.getMeanEma()), "mean=" + s.getMeanEma());
    }

    @Test
    void 실제_지출이_5건이면_표본수가_씨딩값과_같아도_덮어쓰지_않는다() {
        UserStatStats charm = real(StatType.CHARM, 5, "30000");
        seeder.seed(1L, 2_500_000, MealCostBand.UNKNOWN);
        assertMean("30000", charm);
        assertEquals(5, charm.getSampleCount());
    }

    @Test
    void 지출을_지우거나_타입을_옮겨_행이_없어도_남은_기록은_지킨다() {
        // 삭제·카테고리 이동은 user_stat_stats 를 되돌리지 않는다 → 표본 8·평균 3만이 남는다
        UserStatStats charm = real(StatType.CHARM, 8, "30000");
        seeder.seed(1L, 2_500_000, MealCostBand.UNKNOWN);
        seeder.reseedFromIncome(1L, 5_000_000);
        assertMean("30000", charm);
        assertEquals(8, charm.getSampleCount());
    }

    @Test
    void 표본이_없으면_씨딩하고_씨딩값은_월수입_변경때_다시_잡는다() {
        assertTrue(seeder.seed(1L, 2_500_000, MealCostBand.UNKNOWN));
        UserStatStats charm = stats.get(StatType.CHARM);
        assertMean("50000", charm);
        assertEquals(BaselineSeeder.SEEDED_SAMPLE_COUNT, charm.getSampleCount());
        assertTrue(charm.getSeeded());

        seeder.reseedFromIncome(1L, 5_000_000);
        assertMean("100000", charm);
    }

    @Test
    void 씨딩_뒤_실제_지출이_반영되면_더는_덮어쓰지_않는다() {
        seeder.seed(1L, 2_500_000, MealCostBand.UNKNOWN);
        UserStatStats charm = stats.get(StatType.CHARM);
        charm.updateEma(new BigDecimal("46000"), new BigDecimal("80000"), 6); // 실제 지출 1건 반영
        assertFalse(charm.getSeeded());

        seeder.reseedFromIncome(1L, 5_000_000);
        assertMean("46000", charm);
    }
}
