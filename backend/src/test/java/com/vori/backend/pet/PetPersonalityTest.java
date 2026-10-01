package com.vori.backend.pet;

import com.vori.backend.pet.PetPersonality.Temperament;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * 펫 성격 조합 검증. 우세 스탯 판정이 흔들리면 같은 펫의 성격이 요청마다 바뀌어 보인다.
 */
class PetPersonalityTest {

    private static final List<String> KEYS = List.of(
            "kitten", "puppy", "rabbit", "turtle", "deer", "fox", "sheep", "monkey",
            "squirrel", "panda", "raccoon", "penguin", "lion", "dragon", "wolf", "snake");

    private static Pet pet(int energy, int charm, int iq, int endurance) {
        return Pet.builder()
                .userId(1L).speciesId(1L)
                .statEnergy(energy).statCharm(charm).statIq(iq).statEndurance(endurance)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("1등이 2등보다 20% 넘게 앞서면 1등 기질 하나(100%)")
    void dominantStatWins() {
        assertThat(PetPersonality.sharesOf(pet(20, 5, 5, 5))).containsExactly(entry(Temperament.ENERGY, 100));
        assertThat(PetPersonality.sharesOf(pet(5, 20, 5, 5))).containsExactly(entry(Temperament.CHARM, 100));
        assertThat(PetPersonality.sharesOf(pet(5, 5, 20, 5))).containsExactly(entry(Temperament.IQ, 100));
        assertThat(PetPersonality.sharesOf(pet(5, 5, 5, 20))).containsExactly(entry(Temperament.ENDURANCE, 100));
        // 경계 바로 바깥: 10 vs 7 은 30% 차이
        assertThat(PetPersonality.sharesOf(pet(10, 7, 0, 0))).containsExactly(entry(Temperament.ENERGY, 100));
    }

    @Test
    @DisplayName("1·2등 차이가 20% 이하면 균형형 — 차이 d% 만큼 1등 50+d/2 : 2등 50-d/2")
    void closeStatsBlendTopTwoByGap() {
        // 차이 20% → 60:40 (요청 예시)
        assertThat(PetPersonality.sharesOf(pet(10, 8, 0, 0)))
                .containsExactly(entry(Temperament.ENERGY, 60), entry(Temperament.CHARM, 40));
        // 차이 10% → 55:45, 순서는 스탯 큰 쪽 먼저
        assertThat(PetPersonality.sharesOf(pet(0, 18, 20, 3)))
                .containsExactly(entry(Temperament.IQ, 55), entry(Temperament.CHARM, 45));
        // 차이 0% → 50:50, 같은 값이면 ENERGY·CHARM·IQ·ENDURANCE 순
        assertThat(PetPersonality.sharesOf(pet(0, 0, 7, 7)))
                .containsExactly(entry(Temperament.IQ, 50), entry(Temperament.ENDURANCE, 50));
        assertThat(PetPersonality.isBalanced(pet(10, 8, 0, 0))).isTrue();
        assertThat(PetPersonality.isBalanced(pet(10, 7, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("스탯이 모두 0 이면 무던한 균형쟁이")
    void emptyStatsAreBalancedTemperament() {
        assertThat(PetPersonality.sharesOf(pet(0, 0, 0, 0))).containsExactly(entry(Temperament.BALANCED, 100));
        assertThat(PetPersonality.labelOf(pet(0, 0, 0, 0))).isEqualTo("무던한 균형쟁이");
    }

    @Test
    @DisplayName("16종 × 3단계 × 기질 조합이 모두 서로 다른 설명을 만든다")
    void everyCombinationIsDistinct() {
        // 스탯을 작게 둔다 — 레벨이 오르면 displayStage 가 저장된 단계보다 높아져 단계 조합이 겹친다
        int[][] byTemperament = {{3, 0, 0, 0}, {0, 3, 0, 0}, {0, 0, 3, 0}, {0, 0, 0, 3}, {0, 0, 0, 0}};
        Set<String> seen = new HashSet<>();
        for (String key : KEYS) {
            assertThat(PetPersonality.SPECIES).containsKey(key);
            for (PetStage stage : PetStage.values()) {
                for (int[] s : byTemperament) {
                    Pet p = Pet.builder()
                            .userId(1L).speciesId(1L).stage(stage)
                            .statEnergy(s[0]).statCharm(s[1]).statIq(s[2]).statEndurance(s[3])
                            .createdAt(LocalDateTime.now())
                            .build();
                    seen.add(PetPersonality.describe(p, key));
                }
            }
        }
        assertThat(seen).hasSize(16 * 3 * 5);
    }

    @Test
    @DisplayName("균형형은 설명·이름에 두 기질이 비중과 함께 들어가고, 뚜렷하면 한 기질만")
    void describeAndLabelFollowShares() {
        Pet balanced = pet(10, 8, 0, 0);
        assertThat(PetPersonality.describe(balanced, "squirrel"))
                .contains("균형형이야", "활발한 먹보 60%", "다정한 멋쟁이 40%")
                .doesNotContain("꼼꼼한 계산쟁이");
        assertThat(PetPersonality.labelOf(balanced)).isEqualTo("균형형 · 활발한 먹보 60% · 다정한 멋쟁이 40%");

        Pet dominant = pet(0, 0, 20, 5);
        assertThat(PetPersonality.describe(dominant, "squirrel"))
                .contains("꼼꼼한 계산쟁이 100%")
                .doesNotContain("균형형이야");
        assertThat(PetPersonality.labelOf(dominant)).isEqualTo("꼼꼼한 계산쟁이");
    }
}
