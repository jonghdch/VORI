package com.vori.backend.onboarding;

import com.vori.backend.common.StatType;
import com.vori.backend.expense.ExpenseService;
import com.vori.backend.expense.SignalConfig;
import com.vori.backend.expense.SignalConfigService;
import com.vori.backend.stats.UserStatStats;
import com.vori.backend.stats.UserStatStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * 온보딩 답변으로 판정 기준선(user_stat_stats)의 초기값을 채운다.
 *
 * 가입 직후에는 타입별 평균·편차가 0이라 표본 5건이 쌓일 때까지 판정을 건너뛰고 무조건 GREEN 이었다.
 * 여기서 초기값을 넣고 표본 수를 ExpenseService.N_MIN 으로 두면 첫 지출부터 z-score 판정이 돈다.
 * 이후 실제 지출이 EMA(α=0.2)로 초기값을 덮어써 개인 기준으로 바뀐다.
 *
 * 타입별 초기값
 * - ENERGY(식비): 설문의 "평소 한 끼" 구간 중앙값과 편차(MealCostBand). 직접 답이라 월 수입 유도보다 정확하다.
 *   "잘 모르겠어요" 면 채우지 않는다 — 시연 계정이 이 경로를 써서 식비 판정 수치가 종전과 같게 유지된다.
 * - CHARM·IQ·ENDURANCE: 월 수입에서 유도.
 *     평균 = 월수입 × TYPICAL_RATE  ("평소 한 건" 비율)
 *     RED 경계 = 월수입 × RED_RATE  (이 비율을 넘는 한 건은 첫 판정에서 RED)
 *     편차 = (RED 경계 − 평균) ÷ z_red   → z-score 가 정확히 z_red 에서 RED 가 되도록 역산
 *   비율은 잠정값이다. 관리자 설정(signal_config)으로 옮기는 것은 후속 과제.
 *
 * 채우는 조건: 그 타입에 실제 지출이 없을 때만. 씨딩된 뒤 지출이 들어오면 표본 수가 N_MIN 을 넘으므로
 * "표본 수 == N_MIN(씨딩 직후)" 또는 "표본 수 < N_MIN(씨딩 안 됨)" 일 때만 덮어쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaselineSeeder {

    /** 씨딩 시 넣는 표본 수. 판정 게이트를 통과시키기 위한 가상 표본이며 실제 건수가 아니다. */
    public static final int SEEDED_SAMPLE_COUNT = ExpenseService.N_MIN;

    /** 월 수입 대비 "평소 한 건" 비율(%). 학생·사회초년생 가계 기준의 잠정값. */
    static final Map<StatType, BigDecimal> TYPICAL_RATE = new EnumMap<>(Map.of(
            StatType.CHARM, new BigDecimal("0.020"),      // 쇼핑·뷰티: 한 건 2%  (250만 → 5만)
            StatType.IQ, new BigDecimal("0.012"),         // 문화·여가: 한 건 1.2% (250만 → 3만)
            StatType.ENDURANCE, new BigDecimal("0.040")   // 생활·고정비: 한 건 4%  (250만 → 10만)
    ));

    /** 월 수입 대비 첫 판정 RED 경계(%). 이 비율을 넘는 한 건은 이유를 묻는다. */
    static final Map<StatType, BigDecimal> RED_RATE = new EnumMap<>(Map.of(
            StatType.CHARM, new BigDecimal("0.080"),      // 250만 → 20만
            StatType.IQ, new BigDecimal("0.060"),         // 250만 → 15만
            StatType.ENDURANCE, new BigDecimal("0.200")   // 250만 → 50만
    ));

    /** 편차 하한 = 평균의 이 비율. 너무 작으면 평균 근처 지출이 전부 RED 가 된다. */
    static final BigDecimal STDDEV_FLOOR_RATE = new BigDecimal("0.30");

    private final UserStatStatsRepository userStatStatsRepository;
    private final SignalConfigService signalConfigService;

    /**
     * 온보딩 저장 시 호출. 식비는 한 끼 구간으로, 나머지는 월 수입으로 채운다.
     * @return 하나라도 채웠으면 true
     */
    @Transactional
    public boolean seed(Long userId, Integer monthlyIncome, MealCostBand mealCostBand) {
        boolean applied = false;
        if (mealCostBand != null && mealCostBand.midpoint() != null) {
            applied |= apply(userId, StatType.ENERGY,
                    BigDecimal.valueOf(mealCostBand.midpoint()),
                    BigDecimal.valueOf(mealCostBand.initialStddev()));
        }
        applied |= seedFromIncome(userId, monthlyIncome);
        return applied;
    }

    /** 프로필 설정에서 월 수입을 바꿨을 때. 식비는 건드리지 않고 월 수입 유도 타입만 다시 잡는다. */
    @Transactional
    public void reseedFromIncome(Long userId, Integer monthlyIncome) {
        seedFromIncome(userId, monthlyIncome);
    }

    private boolean seedFromIncome(Long userId, Integer monthlyIncome) {
        if (monthlyIncome == null || monthlyIncome <= 0) return false;
        BigDecimal zRed = signalConfigService.getConfig().getZRed();
        BigDecimal income = BigDecimal.valueOf(monthlyIncome);
        boolean applied = false;
        for (StatType type : TYPICAL_RATE.keySet()) {
            Baseline b = derive(income, type, zRed);
            applied |= apply(userId, type, b.mean(), b.stddev());
        }
        return applied;
    }

    record Baseline(BigDecimal mean, BigDecimal stddev) {}

    /** 월 수입 → 타입별 초기 평균·편차. 순수 계산이라 단위 테스트가 가능하다. */
    static Baseline derive(BigDecimal income, StatType type, BigDecimal zRed) {
        BigDecimal mean = income.multiply(TYPICAL_RATE.get(type)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal red = income.multiply(RED_RATE.get(type));
        BigDecimal stddev = red.subtract(mean).divide(zRed, 2, RoundingMode.HALF_UP);
        BigDecimal floor = mean.multiply(STDDEV_FLOOR_RATE).setScale(2, RoundingMode.HALF_UP);
        if (stddev.compareTo(floor) < 0) stddev = floor;
        return new Baseline(mean, stddev);
    }

    /** 실제 지출이 없는 타입에만 쓴다. */
    private boolean apply(Long userId, StatType type, BigDecimal mean, BigDecimal stddev) {
        UserStatStats stats = userStatStatsRepository.findByUserIdAndStatType(userId, type)
                .orElseThrow(() -> new IllegalStateException("user_stat_stats 초기화가 누락되었습니다."));
        int count = stats.getSampleCount();
        boolean untouched = count < ExpenseService.N_MIN || count == SEEDED_SAMPLE_COUNT;
        if (!untouched) return false;
        // 표본이 1~4건인 계정(씨딩 없이 조금 쓴 기존 사용자)은 그 기록을 존중해 건너뛴다.
        if (count > 0 && count < ExpenseService.N_MIN) return false;

        stats.updateEma(mean, stddev, SEEDED_SAMPLE_COUNT);
        log.info("[Onboarding] 기준선 씨딩 — userId={}, type={}, mean={}, stddev={}", userId, type, mean, stddev);
        return true;
    }
}
