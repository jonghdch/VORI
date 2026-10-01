package com.vori.backend.pet.dto;

import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetLevel;
import com.vori.backend.pet.PetStage;
import com.vori.backend.pet.PetSpecies;

import java.time.LocalDateTime;

/**
 * 펫 1마리의 화면 표현. appearanceKey 로 프론트가 이미지 asset 을 찾는다.
 * name 은 사용자가 지어 준 이름 — 아직 짓지 않았으면 null 이고, 프론트가 이름 짓기 팝업을 띄운다.
 * statTotal 은 4대 스탯 합(경험치) — 프론트가 다시 더하지 않도록 서버가 내려준다.
 * level·levelExp·levelExpNeeded 는 PetLevel 규칙으로 계산한 레벨과 현재 레벨 안의 진행도.
 * 만렙(maxLevel)이면 levelExpNeeded 가 0 이다. 프론트는 레벨 공식을 따로 갖지 않는다.
 */
public record PetResponse(
        Long id,
        String name,
        Long speciesId,
        String speciesName,
        String tier,
        String appearanceKey,
        String variant,
        String stage,
        int statEnergy,
        int statCharm,
        int statIq,
        int statEndurance,
        int statTotal,
        int level,
        int levelExp,
        int levelExpNeeded,
        int maxLevel,
        int interactionCount,
        LocalDateTime hatchedAt,
        LocalDateTime releasedAt,
        Integer releaseValue
) {
    public static PetResponse of(Pet p, PetSpecies s) {
        return new PetResponse(
                p.getId(),
                p.getName(),
                p.getSpeciesId(),
                s == null ? null : s.getName(),
                s == null ? null : s.getTier().name(),
                s == null ? null : s.getAppearanceKey(),
                p.getVariant().name(),
                displayStage(p).name(),
                nz(p.getStatEnergy()),
                nz(p.getStatCharm()),
                nz(p.getStatIq()),
                nz(p.getStatEndurance()),
                p.statTotal(),
                p.level(),
                p.statTotal() - PetLevel.minTotalFor(p.level()),
                PetLevel.expToNext(p.level()),
                PetLevel.MAX_LEVEL,
                nz(p.getInteractionCount()),
                p.getHatchedAt(),
                p.getReleasedAt(),
                p.getReleaseValue());
    }

    /**
     * 화면에 보일 단계 — 저장된 단계와 레벨이 정하는 단계 중 높은 쪽.
     * 단계는 스탯이 오를 때만 다시 계산되므로, 진화 기준을 바꾼 직후의 기존 펫은 저장값이 낮을 수 있다.
     * 다음 성장 때 Pet.evaluateStage 가 저장값도 맞춘다.
     */
    private static PetStage displayStage(Pet p) {
        PetStage byLevel = PetLevel.stageFor(p.level());
        return byLevel.ordinal() > p.getStage().ordinal() ? byLevel : p.getStage();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
