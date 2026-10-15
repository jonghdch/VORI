package com.vori.backend.expense;

import com.vori.backend.common.StatType;
import com.vori.backend.stats.UserStatStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpenseCalculationServiceTest {

    private final SignalConfigService configs = mock(SignalConfigService.class);
    private final ExpenseCalculationService service = new ExpenseCalculationService(configs);

    @Test
    void usesConfiguredThresholdsWhenEnoughSamplesExist() {
        when(configs.getConfig()).thenReturn(SignalConfig.builder()
                .zGreen(new BigDecimal("-0.50"))
                .zRed(new BigDecimal("1.50"))
                .build());
        UserStatStats stats = stats("10000.00", "2000.00", 5);

        assertThat(service.calculate(stats, 8000, false).signal()).isEqualTo(Signal.GREEN);
        assertThat(service.calculate(stats, 11000, false).signal()).isEqualTo(Signal.GRAY);
        assertThat(service.calculate(stats, 14000, false).signal()).isEqualTo(Signal.RED);
    }

    @Test
    void recurringExpenseNeverRemainsRed() {
        when(configs.getConfig()).thenReturn(SignalConfig.builder()
                .zGreen(new BigDecimal("-0.50"))
                .zRed(new BigDecimal("1.50"))
                .build());

        ExpenseCalculationService.Result result = service.calculate(
                stats("10000.00", "1000.00", 5), 20000, true);

        assertThat(result.signal()).isEqualTo(Signal.GRAY);
    }

    @Test
    void firstRealExpenseUpdatesEmaAndClearsSeedFlag() {
        UserStatStats stats = stats("10000.00", "2000.00", 5);

        service.updateEma(stats, 12000);

        assertThat(stats.getSampleCount()).isEqualTo(6);
        assertThat(stats.getMeanEma()).isEqualByComparingTo("10400.00");
        assertThat(stats.getSeeded()).isFalse();
    }

    private static UserStatStats stats(String mean, String deviation, int samples) {
        return UserStatStats.builder()
                .userId(1L)
                .statType(StatType.ENERGY)
                .meanEma(new BigDecimal(mean))
                .stddevEma(new BigDecimal(deviation))
                .sampleCount(samples)
                .seeded(true)
                .build();
    }
}
