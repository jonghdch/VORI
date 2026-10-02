package com.vori.backend.pet.dto;

import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetLevel;
import com.vori.backend.pet.PetSpecies;
import com.vori.backend.pettitle.dto.PetTitleSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 펫 1마리의 화면 표현. appearanceKey 로 프론트가 이미지 asset 을 찾는다.
 * name 은 사용자가 지어 준 이름 — 아직 짓지 않았으면 null 이고, 프론트가 이름 짓기 팝업을 띄운다.
 * statTotal 은 4대 스탯 합 — 프론트가 다시 더하지 않도록 서버가 내려준다. exp 는 그 × 10.
 * level·levelExp·levelExpNeeded 는 PetLevel 규칙으로 계산한 레벨과 현재 레벨 안의 진행도(EXP 단위).
 * 만렙(maxLevel)이면 levelExpNeeded 가 0 이다. 프론트는 레벨 공식을 따로 갖지 않는다.
 * titles·equippedTitle 은 이 펫이 딴 칭호와 장착한 칭호 — 분양한 펫이면 분양 순간의 기록이다.
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
        int exp,
        int level,
        int levelExp,
        int levelExpNeeded,
        int maxLevel,
        int interactionCount,
        LocalDateTime hatchedAt,
        LocalDateTime releasedAt,
        Integer releaseValue,
        List<PetTitleSummary> titles,     // 이 펫이 딴 칭호 (획득 순)
        PetTitleSummary equippedTitle     // 장착한 칭호. 장착 안 했으면 null
) {
    /** 칭호를 싣지 않는 응답 — 알 개봉·관리자 도구처럼 칭호가 필요 없는 곳. */
    public static PetResponse of(Pet p, PetSpecies s) {
        return of(p, s, List.of());
    }

    public static PetResponse of(Pet p, PetSpecies s, List<PetTitleSummary> titles) {
        PetTitleSummary equipped = titles.stream()
                .filter(t -> t.awardId().equals(p.getEquippedTitleAwardId()))
                .findFirst().orElse(null);
        return new PetResponse(
                p.getId(),
                p.getName(),
                p.getSpeciesId(),
                s == null ? null : s.getName(),
                s == null ? null : s.getTier().name(),
                s == null ? null : s.getAppearanceKey(),
                p.getVariant().name(),
                p.displayStage().name(),
                nz(p.getStatEnergy()),
                nz(p.getStatCharm()),
                nz(p.getStatIq()),
                nz(p.getStatEndurance()),
                p.statTotal(),
                p.exp(),
                p.level(),
                p.exp() - PetLevel.minExpFor(p.level()),
                PetLevel.expToNext(p.level()),
                PetLevel.MAX_LEVEL,
                nz(p.getInteractionCount()),
                p.getHatchedAt(),
                p.getReleasedAt(),
                p.getReleaseValue(),
                titles,
                equipped);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
