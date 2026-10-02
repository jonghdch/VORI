package com.vori.backend.pettitle;

/**
 * "이 사용자가 키우는 펫의 칭호 조건을 다시 봐 달라" 는 신호.
 *
 * 지출 저장·하루 판정·출석 아이템·AI 답변처럼 펫 지표가 바뀌는 곳에서 발행한다. 판정은 그 트랜잭션이
 * 커밋된 뒤 PetTitleService 가 따로 한다 — 칭호 판정이 실패해도 원래 동작은 그대로 남게.
 *
 * @param reason 어떤 동작이 유발했는지 — 로그 추적용
 */
public record PetTitleCheckEvent(Long userId, String reason) {}
