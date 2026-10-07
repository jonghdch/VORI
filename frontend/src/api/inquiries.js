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

// 사유 칩(reasonCategory)이나 글(answerText) 중 하나는 있어야 한다. 둘 다 있으면 서버가 칩을 우선한다.
// 칩을 고르면 AI 를 부르지 않아 Gemini 한도가 끝나도 답변이 저장된다.
export const answerInquiry = (id, answerText, reasonCategory = null) =>
  post(`/inquiries/${id}/answer`, { answerText: answerText || null, reasonCategory });
