// 백엔드 인증 API 클라이언트.
// - 세션 쿠키 사용: 모든 호출에 credentials: 'include' 필수.
// - 백엔드 CORS 가 http://localhost:3000 을 허용하도록 설정돼 있어야 함.

import { API_BASE } from "./base";
// 서버에 닿지 못했을 때(로그인 화면에 영어 "Failed to fetch" 가 나가던 것)·조회가 오래 걸릴 때의
// 한국어 문구는 http.js 의 send 가 맡는다.
import { send } from "./http";

export class AuthError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "AuthError";
    this.status = status;
  }
}

async function parseErrorMessage(res, fallback) {
  try {
    const body = await res.json();
    return body.message || fallback;
  } catch {
    return fallback;
  }
}

export async function login(email, password) {
  const res = await send(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, password }),
  });

  if (res.status === 401) {
    throw new AuthError("이메일 또는 비밀번호가 올바르지 않습니다", 401);
  }
  if (!res.ok) {
    const msg = await parseErrorMessage(res, `로그인 실패 (${res.status})`);
    throw new AuthError(msg, res.status);
  }
  return res.json(); // { id, email, nickname, role }
}

/**
 * 구글 로그인 — Google Identity Services 가 준 credential(ID 토큰)을 백엔드에 넘긴다.
 * 백엔드가 토큰을 검증하고 계정을 찾거나 만든 뒤 이메일 로그인과 같은 세션 쿠키를 준다.
 * 401 = 토큰 검증 실패, 403 = 이메일 미확인·제재 계정, 503 = 서버에 클라이언트 ID 미설정.
 */
export async function loginWithGoogle(credential) {
  const res = await send(`${API_BASE}/auth/google`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ credential }),
  });
  if (!res.ok) {
    const msg = await parseErrorMessage(res, `구글 로그인 실패 (${res.status})`);
    throw new AuthError(msg, res.status);
  }
  return res.json(); // { id, email, nickname, role }
}

export async function logout() {
  const res = await send(`${API_BASE}/auth/logout`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok && res.status !== 204) {
    throw new AuthError(`로그아웃 실패 (${res.status})`, res.status);
  }
}

export async function me() {
  const res = await send(`${API_BASE}/auth/me`, {
    credentials: "include",
  });
  if (res.status === 401) return null;
  if (!res.ok) {
    throw new AuthError(`사용자 조회 실패 (${res.status})`, res.status);
  }
  return res.json();
}

export async function signup({
  email,
  password,
  nickname,
  name,
  termsAgreed,
  privacyAgreed,
  marketingAgreed,
}) {
  const res = await send(`${API_BASE}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({
      email,
      password,
      nickname,
      name,
      termsAgreed,
      privacyAgreed,
      marketingAgreed: !!marketingAgreed,
    }),
  });

  // 서버가 준 문구를 먼저 쓴다. 400 은 이메일 형식·닉네임 길이·비밀번호·약관 중 무엇이든 될 수 있고
  // (SignupRequest 가 항목마다 문구를 준다), 409 는 이미 쓰는 이메일과 탈퇴 대기 계정이 문구가 다르다.
  // 아래 문구는 본문이 없을 때만 쓴다.
  if (res.status === 409) {
    const msg = await parseErrorMessage(res, "이미 사용 중인 이메일입니다");
    throw new AuthError(msg, 409);
  }
  if (res.status === 400) {
    const msg = await parseErrorMessage(
      res,
      "입력값을 확인해 주세요 (비밀번호 8자 이상·특수문자 포함, 필수 약관 동의)"
    );
    throw new AuthError(msg, 400);
  }
  if (!res.ok) {
    const msg = await parseErrorMessage(res, `회원가입 실패 (${res.status})`);
    throw new AuthError(msg, res.status);
  }
  return res.json();
}
