// 공통 fetch 래퍼 — 세션 쿠키 포함, 실패 시 status 를 실은 Error 를 던진다.
//
// silent failure 금지: 401/500 을 null 로 삼키면 "데이터 없음" 과 "조회 실패" 가
// 구분되지 않는다. 처리(401 → 로그인 유도, 400 → 안내 문구)는 호출부 책임.
import { API_BASE } from "./base";

// 백엔드는 ResponseStatusException 만 던지고 message 를 본문에 싣지 않으므로
// 상태 코드별 기본 문구를 프론트가 가진다. 본문에 message 가 있으면 그걸 우선.
const STATUS_MESSAGE = {
  400: "요청을 처리할 수 없어요",
  401: "로그인이 필요합니다",
  403: "권한이 없어요",
  404: "찾을 수 없어요",
  409: "이미 처리된 요청이에요",
};

async function request(path, { method = "GET", body } = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    credentials: "include",
    headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    let msg = STATUS_MESSAGE[res.status] || `요청 실패 (${res.status})`;
    try {
      const data = await res.json();
      if (data?.message) msg = data.message;
    } catch {}
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }

  // 204 No Content 또는 본문 "null"(펫 없음 등) 은 null 로.
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const get = (path) => request(path);
export const post = (path, body) => request(path, { method: "POST", body });
