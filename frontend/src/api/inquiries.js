// AI 분석 질문 API 클라이언트.
// GET /api/inquiries?date=...  : 그 날짜의 미답변 질문 목록
// POST /api/inquiries/{id}/answer : 답변 제출

const API_BASE = "http://localhost:8080/api";

export async function listInquiriesByDate(date) {
  const res = await fetch(`${API_BASE}/inquiries?date=${date}`, {
    method: "GET",
    credentials: "include",
  });
  if (!res.ok) return [];
  return res.json();
}

export async function answerInquiry(id, answerText) {
  const res = await fetch(`${API_BASE}/inquiries/${id}/answer`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ answerText }),
  });
  if (!res.ok) {
    let msg = `${res.status}`;
    try {
      const data = await res.json();
      msg = data.message || msg;
    } catch {}
    throw new Error(msg);
  }
}
