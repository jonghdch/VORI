// 공통 fetch 래퍼 — 세션 쿠키 포함, 실패 시 status 를 실은 Error 를 던진다.
//
// silent failure 금지: 401/500 을 null 로 삼키면 "데이터 없음" 과 "조회 실패" 가
// 구분되지 않는다. 처리(401 → 로그인 유도, 400 → 안내 문구)는 호출부 책임.
import { API_BASE } from "./base";

// 백엔드가 GlobalExceptionHandler 로 사용자에게 보여줄 한글 message 를 본문에 실어 준다.
// 그러므로 message 가 있으면 그걸 쓰는 게 원칙이다 — 아래 표는 message 가 없는 경우
// (Security 필터가 만드는 401 등)에만 쓰는 최후 문구다.
//
// 호출부에서 상태 코드로 문구를 정하지 말 것. 같은 코드에 사유가 추가되면 틀린 안내가 나간다.
// 실제로 개봉 409 가 "이미 개봉한 알" 하나였다가 "키우던 펫이 있음" 이 추가되면서 그랬다.
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
