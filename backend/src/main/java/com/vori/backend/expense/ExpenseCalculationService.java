package com.vori.backend.expense;

import com.vori.backend.stats.UserStatStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 지출 금액을 현재 사용자 통계와 비교해 신호·z-score·절약액을 계산한다.
 * 저장과 보상은 다루지 않아 등록·수정 양쪽이 같은 판정 규칙을 공유할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ExpenseCalculationService {

    public static final int N_MIN = 5;
    private static final BigDecimal STDDEV_MIN = new BigDecimal("0.01");
    // expenses.z_score 는 DECIMAL(6,3) — 담을 수 있는 한계. clampZScore 참조.
    private static final BigDecimal Z_SCORE_MAX = new BigDecimal("999.999");
    private static final BigDecimal Z_SCORE_MIN = new BigDecimal("-999.999");
    private static final double EMA_ALPHA = 0.2;

    private final SignalConfigService signalConfigService;

    public Result calculate(UserStatStats stats, int amount, boolean recurring) {
        BigDecimal zScore = null;
        Signal signal;

        if (stats.getSampleCount() < N_MIN || stats.getStddevEma().compareTo(STDDEV_MIN) < 0) {
            signal = Signal.GREEN;
        } else {
            zScore = clampZScore(BigDecimal.valueOf(amount)
                    .subtract(stats.getMeanEma())
                    .divide(stats.getStddevEma(), 3, RoundingMode.HALF_UP));
            SignalConfig config = signalConfigService.getConfig();
            signal = zScore.compareTo(config.getZGreen()) <= 0 ? Signal.GREEN
                    : (zScore.compareTo(config.getZRed()) <= 0 ? Signal.GRAY : Signal.RED);
        }

        // 반복 결제는 사용자의 의식적인 단건 결정이 아니므로 RED 자동 제외.
        if (recurring && signal == Signal.RED) signal = Signal.GRAY;

        int savedAmount = stats.getMeanEma().subtract(BigDecimal.valueOf(amount)).intValue();
        return new Result(zScore, signal, savedAmount);
    }

    public void updateEma(UserStatStats stats, int amount) {
        int newCount = stats.getSampleCount() + 1;
        if (stats.getSampleCount() == 0) {
            stats.updateEma(BigDecimal.valueOf(amount), BigDecimal.ZERO, newCount);
            return;
        }

        double oldMean = stats.getMeanEma().doubleValue();
        double oldVar = Math.pow(stats.getStddevEma().doubleValue(), 2);
        double newMean = EMA_ALPHA * amount + (1 - EMA_ALPHA) * oldMean;
        double deviation = amount - oldMean;
        double newVar = EMA_ALPHA * deviation * deviation + (1 - EMA_ALPHA) * oldVar;

        stats.updateEma(
                BigDecimal.valueOf(newMean).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(Math.sqrt(newVar)).setScale(2, RoundingMode.HALF_UP),
                newCount
        );
    }

    /**
     * z_score 를 컬럼이 담을 수 있는 범위로 자른다.
     *
     * expenses.z_score 가 DECIMAL(6,3) 이라 ±999.999 를 넘으면 저장 시 Data truncation 이
     * 나고 지출 등록 자체가 500 으로 실패한다. 평소 소비가 일정해 stddev 가 작은 사용자가
     * 큰 지출을 한 번 하면 z 가 네 자리로 나오는데, 그게 바로 VORI 가 잡으라고 만든
     * 상황이라 하필 거기서 앱이 죽는다.
     *
     * z 가 1000 을 넘으면 어차피 RED 이고, 1300 인지 1500 인지는 판정에도 화면에도
     * 의미가 없으므로 잘라 담는다. 컬럼을 넓히는 방법도 있지만 stddev 가 더 작아지면
     * 같은 문제가 다시 생기므로 근본 대책이 못 된다.
     */
    private static BigDecimal clampZScore(BigDecimal z) {
        if (z.compareTo(Z_SCORE_MAX) > 0) return Z_SCORE_MAX;
        if (z.compareTo(Z_SCORE_MIN) < 0) return Z_SCORE_MIN;
        return z;
    }

    public record Result(BigDecimal zScore, Signal signal, int savedAmount) {}
}
