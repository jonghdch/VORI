package com.vori.backend.pettitle;

import java.util.function.ToLongFunction;

/**
 * 펫 칭호 조건 계산 타입. 모두 "이 펫" 기준이다 — 펫이 바뀌면 값도 0부터 다시 센다.
 * 칭호 이름·설명·목표값은 DB pet_titles 에 두고, 지표 계산 로직만 코드가 책임진다.
 */
public enum PetTitleMetricType {
    /** 펫 레벨 (진화 칭호). */
    LEVEL(PetTitleProgress::level),
    /** 이 펫과의 상호작용 횟수. */
    INTERACTIONS(PetTitleProgress::interactions),
    /** 이 펫과 지내는 동안 답한 AI 질문 수 (질문이 달린 지출도 그 기간에 기록된 것만). */
    AI_ANSWERS(PetTitleProgress::aiAnswers),
    /** 상호작용 매력 보너스(1% 확률)를 받은 횟수. */
    CHARM_BONUS(PetTitleProgress::charmBonuses);

    private final ToLongFunction<PetTitleProgress> current;

    PetTitleMetricType(ToLongFunction<PetTitleProgress> current) {
        this.current = current;
    }

    public long currentOf(PetTitleProgress progress) {
        return current.applyAsLong(progress);
    }
}
