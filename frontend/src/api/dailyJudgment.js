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
