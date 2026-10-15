package com.vori.backend.pettitle;

/**
 * 펫 칭호 조건 평가에 필요한 펫 지표 묶음. 지표를 추가할 때는 여기와 PetTitleService.collect() 만 손대면 된다.
 */
public record PetTitleProgress(
        long level,         // 펫 레벨
        long interactions,  // 상호작용 횟수
        long aiAnswers,     // 이 펫과 지내는 동안 답한 AI 질문 수
        long charmBonuses   // 상호작용 매력 보너스 횟수
) {
    /** 키우는 펫이 없을 때 — 과제 목록을 0% 로 미리 보여 준다. */
    public static final PetTitleProgress ZERO = new PetTitleProgress(0, 0, 0, 0);
}
