// 일일 리포트 API — 매일 00:10 배치가 전일 리포트를 만들고, 홈에서 펫 말풍선으로 보여준다.
import { get, post } from "./http";

/**
 * @typedef {{
 *   id:number, reportDate:string, incomeTotal:number, expenseTotal:number, savedAmount:number,
 *   statDeltaTotal:number|null, petSnapshot:string|null, aiComment:string|null,
 *   generatedAt:string, readAt:string|null
 * }} DailyReport
 */

/** 가장 최근 리포트. 없으면 null. @returns {Promise<DailyReport|null>} */
export const getLatestDailyReport = () => get("/daily-reports/today");

/** @returns {Promise<DailyReport|null>} */
export const getDailyReport = (isoDate) => get(`/daily-reports/${isoDate}`);

/** @returns {Promise<DailyReport[]>} 최신순 */
export const listDailyReports = (from, to) => get(`/daily-reports?from=${from}&to=${to}`);

/** 읽음 처리 (204). 이미 읽은 건은 최초 시각 보존. */
export const markDailyReportRead = (id) => post(`/daily-reports/${id}/read`);

/** 본인 리포트 즉시 생성 — 시연·디버깅용. date 생략 시 어제. */
export const generateDailyReport = (isoDate) =>
  post(`/daily-reports/generate${isoDate ? `?date=${isoDate}` : ""}`);
