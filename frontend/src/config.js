// 앱 동작 설정 — 환경마다 달라지는 값만 모은다.
// CRA 는 REACT_APP_ 접두사가 붙은 환경변수만 빌드에 노출한다.

/**
 * AI 소비 판정이 열리는 시각(0~23). 기본 20시.
 *
 * 하루가 끝난 뒤 그날 소비를 한 번에 돌아보게 하려는 규칙이다(1학기 4차 회의 확정).
 * 다만 발표·시연은 낮에 하므로 그대로 두면 핵심 기능을 보여줄 수 없어, 값을 밖으로 뺐다.
 *
 *   frontend/.env.local 에  REACT_APP_AI_ACTIVE_FROM_HOUR=0  → 하루 종일 열림
 *
 * 운영 기본값은 20 이다. 시연용으로 바꿨다면 되돌리는 것을 잊지 말 것.
 */
export const AI_ACTIVE_FROM_HOUR = (() => {
  const raw = process.env.REACT_APP_AI_ACTIVE_FROM_HOUR;
  if (raw == null || raw === "") return 20;
  const parsed = Number(raw);
  // 잘못된 값이 들어와도 기능이 죽지 않게 기본값으로 되돌린다.
  return Number.isInteger(parsed) && parsed >= 0 && parsed <= 23 ? parsed : 20;
})();

/** 지금이 AI 판정 가능 시각인가. 버튼 활성·페이지 가드가 같은 기준을 쓰도록 한 곳에 둔다. */
export function isAiJudgeOpen(now = new Date()) {
  return now.getHours() >= AI_ACTIVE_FROM_HOUR;
}
