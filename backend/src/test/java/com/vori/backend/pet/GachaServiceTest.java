package com.vori.backend.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 가챠 확률 검증. 상금(펫 등급)이 걸린 로직이라 분포가 표와 어긋나면 바로 잡아야 한다.
 * Spring 컨텍스트·DB 없이 도는 순수 단위 테스트.
 */
class GachaServiceTest {

    private static final int TRIALS = 100_000;
    /** 허용 오차(퍼센트포인트). 10만 회에서 1%p 는 30σ 이상이라 정상 변동으로는 절대 안 넘는다. */
    private static final double TOLERANCE_PP = 0.5;

    private GachaService serviceWithAllTiers() {
        PetSpeciesRepository repo = mock(PetSpeciesRepository.class);
        // 등급별로 종족 1개씩만 둬서 draw 결과의 tier 를 바로 셀 수 있게 한다
        when(repo.findByTier(any())).thenAnswer(inv -> {
            PetTier tier = inv.getArgument(0);
            return List.of(PetSpecies.builder()
                    .id((long) tier.ordinal())
                    .name("종족-" + tier)
                    .tier(tier)
                    .appearanceKey("key-" + tier)
                    .build());
        });
        return new GachaService(repo);
    }

    private Map<PetTier, Integer> countTiers(GachaService service, EggGrade grade) {
        Map<PetTier, Integer> counts = new EnumMap<>(PetTier.class);
        for (int i = 0; i < TRIALS; i++) {
            PetTier drawn = service.draw(grade.distribution()).species().getTier();
            counts.merge(drawn, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    @DisplayName("모든 알 등급의 확률 합은 정확히 100 이다")
    void distributionsSumTo100() {
        for (EggGrade grade : EggGrade.values()) {
            int sum = grade.distribution().values().stream().mapToInt(Integer::intValue).sum();
            assertThat(sum).as("%s 확률 합", grade).isEqualTo(100);
        }
    }

    @Test
    @DisplayName("추첨 결과가 각 등급의 확률표를 따른다")
    void drawFollowsDeclaredDistribution() {
        GachaService service = serviceWithAllTiers();

        for (EggGrade grade : EggGrade.values()) {
            Map<PetTier, Integer> counts = countTiers(service, grade);

            grade.distribution().forEach((tier, expectedPct) -> {
                double actualPct = counts.getOrDefault(tier, 0) * 100.0 / TRIALS;
                assertThat(actualPct)
                        .as("%s 의 %s 등급 비율", grade, tier)
                        .isCloseTo(expectedPct, org.assertj.core.data.Offset.offset(TOLERANCE_PP));
            });
        }
    }

    @Test
    @DisplayName("확률 0% 인 등급은 절대 뽑히지 않는다")
    void zeroWeightTierNeverDrawn() {
        GachaService service = serviceWithAllTiers();
        // 최고급 알은 C 등급이 0%
        assertThat(EggGrade.LEGENDARY.distribution().get(PetTier.C)).isZero();

        Map<PetTier, Integer> counts = countTiers(service, EggGrade.LEGENDARY);
        assertThat(counts.getOrDefault(PetTier.C, 0)).isZero();
    }

    @Test
    @DisplayName("변종은 NORMAL 90 / IRO 8 / ALIEN 2 비율로 나온다")
    void variantDistribution() {
        GachaService service = serviceWithAllTiers();

        Map<PetVariant, Integer> counts = new EnumMap<>(PetVariant.class);
        for (int i = 0; i < TRIALS; i++) {
            counts.merge(service.draw(EggGrade.BASIC.distribution()).variant(), 1, Integer::sum);
        }

        Map<PetVariant, Integer> expected = Map.of(
                PetVariant.NORMAL, 90, PetVariant.IRO, 8, PetVariant.ALIEN, 2);
        expected.forEach((variant, pct) -> {
            double actualPct = counts.getOrDefault(variant, 0) * 100.0 / TRIALS;
            assertThat(actualPct)
                    .as("변종 %s 비율", variant)
                    .isCloseTo(pct, org.assertj.core.data.Offset.offset(TOLERANCE_PP));
        });
    }

    @Test
    @DisplayName("종족 시드가 비어 있으면 명확한 예외로 실패한다")
    void emptySpeciesPoolFailsLoudly() {
        PetSpeciesRepository repo = mock(PetSpeciesRepository.class);
        when(repo.findByTier(any())).thenReturn(List.of());

        GachaService service = new GachaService(repo);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.draw(EggGrade.BASIC.distribution())
        )).hasMessageContaining("시드 누락");
    }
}
