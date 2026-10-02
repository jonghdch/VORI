// 월 지출 예산 API — /api/budgets.
// 예산을 정하지 않은 달도 404 가 아니라 budgetSet=false 로 온다. 사용액·잔액·사용률은 서버가 계산한다.
import { del, get, put } from "./http";

/** GET /budgets?yearMonth=2026-10 → { yearMonth, amount, spent, remaining, usagePct, exceeded, budgetSet } */
export const getBudget = (yearMonth) => get(`/budgets?yearMonth=${yearMonth}`);

/** PUT /budgets — 설정과 변경이 같은 호출이다(월당 1건). */
export const saveBudget = (yearMonth, amount) => put("/budgets", { yearMonth, amount });

/** DELETE /budgets/2026-10 — 예산 해제. 없어도 성공한다. */
export const deleteBudget = (yearMonth) => del(`/budgets/${yearMonth}`);
