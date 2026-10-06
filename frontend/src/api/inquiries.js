// AI 분석 질문 API 클라이언트.
// GET /api/inquiries?date=...  : 그 날짜의 미답변 질문 목록
// POST /api/inquiries/{id}/answer : 답변 제출

import { get, post } from "./http";

export async function listInquiriesByDate(date) {
  try {
    return await get(`/inquiries?date=${encodeURIComponent(date)}`);
  } catch {
    return [];
  }
}

export const answerInquiry = (id, answerText) =>
  post(`/inquiries/${id}/answer`, { answerText });
