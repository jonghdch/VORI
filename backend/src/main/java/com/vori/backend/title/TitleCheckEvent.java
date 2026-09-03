package com.vori.backend.title;

/**
 * "이 사용자의 칭호 조건을 다시 봐 달라" 는 신호.
 *
 * 지출 등록·펫 분양·목표 달성처럼 지표가 바뀌는 곳에서 발행한다. 각 서비스가 어떤 칭호가
 * 있는지 알 필요 없이 이벤트 한 줄만 던지면 되므로, 칭호가 늘어나도 그 서비스들은 그대로다.
 *
 * @param reason 어떤 동작이 유발했는지 — 로그 추적용
 */
public record TitleCheckEvent(Long userId, String reason) {}
