package com.vori.backend.onboarding;

import com.vori.backend.common.StatType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 월 수입 → 초기 평균·편차 역산 규칙. 네트워크·DB 없이 순수 계산만 본다. */
class BaselineSeederTest {

    private static final BigDecimal INCOME = new BigDecimal("2500000");
    private static final BigDecimal Z_RED = new BigDecimal("2.00");

    @Test
    void 쇼핑_250만원_수입이면_평균5만_편차7만5천() {
        BaselineSeeder.Baseline b = BaselineSeeder.derive(INCOME, StatType.CHARM, Z_RED);
        assertEquals(new BigDecimal("50000.00"), b.mean());
        assertEquals(new BigDecimal("75000.00"), b.stddev()); // (20만 − 5만) ÷ 2
    }

    @Test
    void RED_경계_금액은_정확히_z_red_에_떨어진다() {
        for (StatType t : BaselineSeeder.TYPICAL_RATE.keySet()) {
            BaselineSeeder.Baseline b = BaselineSeeder.derive(INCOME, t, Z_RED);
            BigDecimal redAmount = INCOME.multiply(BaselineSeeder.RED_RATE.get(t));
            BigDecimal z = redAmount.subtract(b.mean()).divide(b.stddev(), 3, java.math.RoundingMode.HALF_UP);
            assertEquals(0, Z_RED.compareTo(z.setScale(2, java.math.RoundingMode.HALF_UP)), "type=" + t);
        }
    }

    @Test
    void z_red_가_커지면_편차가_줄어_판정이_예민해진다() {
        BaselineSeeder.Baseline loose = BaselineSeeder.derive(INCOME, StatType.IQ, new BigDecimal("2.00"));
        BaselineSeeder.Baseline strict = BaselineSeeder.derive(INCOME, StatType.IQ, new BigDecimal("3.00"));
        assertTrue(strict.stddev().compareTo(loose.stddev()) < 0);
    }

    @Test
    void 편차는_평균의_30퍼센트_아래로_내려가지_않는다() {
        // z_red 를 아주 크게 주면 역산 편차가 0 에 가까워진다 → 하한이 잡아야 한다
        BaselineSeeder.Baseline b = BaselineSeeder.derive(INCOME, StatType.ENDURANCE, new BigDecimal("1000"));
        BigDecimal floor = b.mean().multiply(BaselineSeeder.STDDEV_FLOOR_RATE);
        assertEquals(0, floor.setScale(2).compareTo(b.stddev()));
    }
}
