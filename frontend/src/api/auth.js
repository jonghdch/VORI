// 백엔드 인증 API 클라이언트.
// - 세션 쿠키 사용: 모든 호출에 credentials: 'include' 필수.
// - 백엔드 CORS 가 http://localhost:3000 을 허용하도록 설정돼 있어야 함.

const API_BASE = "http://localhost:8080/api";

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
  const res = await fetch(`${API_BASE}/auth/login`, {
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

export async function logout() {
  const res = await fetch(`${API_BASE}/auth/logout`, {
    method: "POST",
    credentials: "include",
  });
  if (!res.ok && res.status !== 204) {
    throw new AuthError(`로그아웃 실패 (${res.status})`, res.status);
  }
}

export async function me() {
  const res = await fetch(`${API_BASE}/auth/me`, {
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
  const res = await fetch(`${API_BASE}/auth/signup`, {
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

  if (res.status === 409) {
    throw new AuthError("이미 사용 중인 이메일입니다", 409);
  }
  if (res.status === 400) {
    throw new AuthError(
      "입력값을 확인해 주세요 (비밀번호 8자 이상·특수문자 포함, 필수 약관 동의)",
      400
    );
  }
  if (!res.ok) {
    const msg = await parseErrorMessage(res, `회원가입 실패 (${res.status})`);
    throw new AuthError(msg, res.status);
  }
  return res.json();
}
