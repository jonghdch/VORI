// AI 분석 질문 API 클라이언트.
// GET /api/inquiries?date=...  : 그 날짜의 미답변 질문 목록
// POST /api/inquiries/{id}/answer : 답변 제출

import { get, post } from "./http";

// 실패는 그대로 던진다 — 빈 목록으로 바꾸지 않는다. 판정 화면은 질문이 0개면 1차 판정을 바로 확정하는데,
// 확정은 하루 한 번이라 되돌릴 수 없다. 조회 실패를 "질문 없음"으로 읽으면 사유를 답할 기회 없이 확정된다.
export const listInquiriesByDate = (date) =>
  get(`/inquiries?date=${encodeURIComponent(date)}`);

// 사유 칩(reasonCategory)이나 글(answerText) 중 하나는 있어야 한다. 둘 다 있으면 서버가 칩을 우선한다.
// 칩을 고르면 AI 를 부르지 않아 Gemini 한도가 끝나도 답변이 저장된다.
export const answerInquiry = (id, answerText, reasonCategory = null) =>
  post(`/inquiries/${id}/answer`, { answerText: answerText || null, reasonCategory });
