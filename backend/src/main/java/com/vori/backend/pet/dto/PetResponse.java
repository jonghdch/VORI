package com.vori.backend.pet.dto;

import com.vori.backend.pet.Pet;
import com.vori.backend.pet.PetSpecies;

import java.time.LocalDateTime;

/**
 * 펫 1마리의 화면 표현. appearanceKey 로 프론트가 이미지 asset 을 찾는다.
 * statTotal 은 4대 스탯 합(진화 기준값) — 프론트가 다시 더하지 않도록 서버가 내려준다.
 */
public record PetResponse(
        Long id,
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
        LocalDateTime hatchedAt,
        LocalDateTime releasedAt,
        Integer releaseValue
) {
    public static PetResponse of(Pet p, PetSpecies s) {
        return new PetResponse(
                p.getId(),
                p.getSpeciesId(),
                s == null ? null : s.getName(),
                s == null ? null : s.getTier().name(),
                s == null ? null : s.getAppearanceKey(),
                p.getVariant().name(),
                p.getStage().name(),
                nz(p.getStatEnergy()),
                nz(p.getStatCharm()),
                nz(p.getStatIq()),
                nz(p.getStatEndurance()),
                p.statTotal(),
                p.getHatchedAt(),
                p.getReleasedAt(),
                p.getReleaseValue());
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
