package com.vori.backend.report;

/**
 * 리포트 생성 시점의 펫 상태. daily_reports.pet_snapshot 에 JSON 으로 저장한다.
 * 나중에 그 펫을 분양해도 리포트에는 그날의 모습이 그대로 남는다.
 */
public record PetSnapshot(
        Long petId,
        String speciesName,
        String appearanceKey,
        String stage,
        Integer statEnergy,
        Integer statCharm,
        Integer statIq,
        Integer statEndurance,
        Integer statTotal
) {}
