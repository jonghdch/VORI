package com.vori.backend.pet;

import com.vori.backend.pet.dto.PetResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 펫 레벨·진화 규칙. EXP = 스탯 합 × 10, 레벨은 EXP 에서 나오고, 레벨업에 드는 경험치는 점점 커진다.
 * 5레벨에 2차, 15레벨에 3차로 진화하고 30레벨(만렙)이 배웅 가능이다.
 */
class PetLevelTest {

    @Test
    @DisplayName("레벨업에 드는 EXP 는 40 부터 2레벨마다 10씩 늘어난다")
    void expToNextGrows() {
        assertThat(PetLevel.expToNext(1)).isEqualTo(40);
        assertThat(PetLevel.expToNext(2)).isEqualTo(40);
        assertThat(PetLevel.expToNext(3)).isEqualTo(50);
        assertThat(PetLevel.expToNext(10)).isEqualTo(80);
        assertThat(PetLevel.expToNext(29)).isEqualTo(180);
        assertThat(PetLevel.expToNext(30)).isZero(); // 만렙은 다음이 없다
        for (int lv = 2; lv < PetLevel.MAX_LEVEL; lv++) {
            assertThat(PetLevel.expToNext(lv)).isGreaterThanOrEqualTo(PetLevel.expToNext(lv - 1));
        }
    }

    @Test
    @DisplayName("레벨별 누적 EXP — 5레벨 180, 15레벨 980, 30레벨 3,120")
    void cumulative() {
        assertThat(PetLevel.minExpFor(1)).isZero();
        assertThat(PetLevel.minExpFor(5)).isEqualTo(180);
        assertThat(PetLevel.minExpFor(15)).isEqualTo(980);
        assertThat(PetLevel.minExpFor(30)).isEqualTo(3120);
    }

    @Test
    @DisplayName("EXP = 스탯 합 × 10")
    void expIsTenTimesStatTotal() {
        assertThat(pet(18).exp()).isEqualTo(180);
        assertThat(pet(18).level()).isEqualTo(5);
        assertThat(pet(17).level()).isEqualTo(4);
    }

    @Test
    @DisplayName("EXP → 레벨, 만렙 위로는 30")
    void levelFor() {
        assertThat(PetLevel.levelFor(0)).isEqualTo(1);
        assertThat(PetLevel.levelFor(39)).isEqualTo(1);
        assertThat(PetLevel.levelFor(40)).isEqualTo(2);
        assertThat(PetLevel.levelFor(179)).isEqualTo(4);
        assertThat(PetLevel.levelFor(180)).isEqualTo(5);
        assertThat(PetLevel.levelFor(3119)).isEqualTo(29);
        assertThat(PetLevel.levelFor(3120)).isEqualTo(30);
        assertThat(PetLevel.levelFor(99999)).isEqualTo(30);
    }

    @Test
    @DisplayName("5레벨에 2차, 15레벨에 3차로 진화한다")
    void stageByLevel() {
        assertThat(stageAfter(17)).isEqualTo(PetStage.INFANT);
        assertThat(stageAfter(18)).isEqualTo(PetStage.JUVENILE);
        assertThat(stageAfter(97)).isEqualTo(PetStage.JUVENILE);
        assertThat(stageAfter(98)).isEqualTo(PetStage.ADULT);
        assertThat(Pet.minStatTotalFor(PetStage.JUVENILE)).isEqualTo(18);
        assertThat(Pet.minStatTotalFor(PetStage.ADULT)).isEqualTo(98);
    }

    @Test
    @DisplayName("30레벨이 돼야 배웅 가능")
    void graduation() {
        assertThat(pet(311).isGraduated()).isFalse();
        assertThat(pet(312).isGraduated()).isTrue();
    }

    @Test
    @DisplayName("응답에 레벨과 현재 레벨 안의 진행 경험치가 실린다")
    void responseCarriesLevel() {
        PetResponse r = PetResponse.of(pet(52 + 3), null); // 스탯 합 52 = EXP 520 = 10레벨 시작
        assertThat(r.exp()).isEqualTo(550);
        assertThat(r.level()).isEqualTo(10);
        assertThat(r.levelExp()).isEqualTo(30);
        assertThat(r.levelExpNeeded()).isEqualTo(PetLevel.expToNext(10));
        assertThat(r.maxLevel()).isEqualTo(30);

        PetResponse max = PetResponse.of(pet(312 + 50), null);
        assertThat(max.level()).isEqualTo(30);
        assertThat(max.levelExpNeeded()).isZero();
    }

    @Test
    @DisplayName("관리자 레벨 강제 — 그 레벨의 최소 경험치와 단계로 맞춘다")
    void forceLevel() {
        Pet p = pet(0);
        p.forceLevel(30);
        assertThat(p.statTotal()).isEqualTo(312);
        assertThat(p.exp()).isEqualTo(3120);
        assertThat(p.getStage()).isEqualTo(PetStage.ADULT);
        p.forceLevel(3);
        assertThat(p.level()).isEqualTo(3);
        assertThat(p.getStage()).isEqualTo(PetStage.INFANT);
    }

    @Test
    @DisplayName("기준이 바뀌기 전에 저장된 단계가 낮아도, 응답은 레벨에 맞는 단계를 보여 준다")
    void responseStageFollowsLevel() {
        Pet old = Pet.builder().id(1L).userId(1L).speciesId(1L)
                .statEnergy(200).stage(PetStage.JUVENILE)
                .createdAt(LocalDateTime.now()).build();
        assertThat(PetResponse.of(old, null).stage()).isEqualTo("ADULT");
        // 반대로 저장된 단계가 더 높으면(관리자 강제 등) 그대로 둔다 — 단계는 내려가지 않는다
        Pet forced = Pet.builder().id(2L).userId(1L).speciesId(1L)
                .statEnergy(0).stage(PetStage.ADULT)
                .createdAt(LocalDateTime.now()).build();
        assertThat(PetResponse.of(forced, null).stage()).isEqualTo("ADULT");
    }

    @Test
    @DisplayName("표시 단계는 Pet.displayStage 한 곳 — 리포트 스냅샷도 같은 값을 쓴다")
    void displayStageOnPet() {
        Pet old = Pet.builder().id(3L).userId(1L).speciesId(1L)
                .statEnergy(150).stage(PetStage.INFANT)
                .createdAt(LocalDateTime.now()).build();
        assertThat(old.displayStage()).isEqualTo(PetStage.ADULT);
    }

    private static PetStage stageAfter(int total) {
        Pet p = pet(total);
        p.evaluateStage();
        return p.getStage();
    }

    private static Pet pet(int total) {
        return Pet.builder()
                .id(1L).userId(1L).speciesId(1L)
                .statEnergy(total)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
