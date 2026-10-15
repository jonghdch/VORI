import { get, post } from "./http";

export const getTodayJudgment = () =>
  get("/daily-judgments/today");

export const getDateJudgment = (date) =>
  get(`/daily-judgments?date=${encodeURIComponent(date)}`);

export const getMonthlyJudgments = (month) =>
  get(`/daily-judgments/month?month=${encodeURIComponent(month)}`);

export const startTodayJudgment = () =>
  post("/daily-judgments/today");

export const startDateJudgment = (date) =>
  post(`/daily-judgments?date=${encodeURIComponent(date)}`);

// 예외 지출 사유 입력을 마쳤거나 건너뛰었을 때 — 그날 판정을 확정하고 보상을 받는다 (docs/judgment-flow.md ③)
export const finalizeDateJudgment = (date) =>
  post(`/daily-judgments/finalize?date=${encodeURIComponent(date)}`);
