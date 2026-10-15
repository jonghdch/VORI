// 월간(보이는) 리포트 정산 기록 API — 매월 마지막 날 12시에 정산된다.
//   GET  /api/monthly-reports                 정산된 리포트 목록(최신 달부터)
//   GET  /api/monthly-reports/unread          안 본 가장 최근 리포트 (없으면 null)
//   POST /api/monthly-reports/{yearMonth}/read
//   POST /api/monthly-reports/generate?month= 본인 리포트 즉시 정산(시연·확인용)
import { get, post } from "./http";

/**
 * @typedef {{ yearMonth:string, expenseTotal:number, expenseCount:number, incomeTotal:number,
 *   judgedDays:number, greenDays:number, grayDays:number, redDays:number,
 *   generatedAt:string, read:boolean }} MonthlyReport
 */

/** @returns {Promise<MonthlyReport[]>} */
export const listMonthlyReports = () => get("/monthly-reports");

/** @returns {Promise<MonthlyReport|null>} */
export const getUnreadMonthlyReport = () => get("/monthly-reports/unread");

export const markMonthlyReportRead = (yearMonth) => post(`/monthly-reports/${yearMonth}/read`);

export const generateMonthlyReport = (yearMonth) =>
  post(`/monthly-reports/generate${yearMonth ? `?month=${yearMonth}` : ""}`);
