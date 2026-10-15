// 어드민 전용 API 클라이언트.
// 전 경로가 백엔드에서 hasRole("ADMIN") 으로 보호됨 → 일반 유저는 403.
// 세션 쿠키 인증이라 credentials: 'include' 필수.

import { API_BASE } from "./base";

export class AdminApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "AdminApiError";
    this.status = status;
  }
}

/**
 * 유저 현황 목록 조회.
 * @param {{ page?: number, size?: number, role?: "USER"|"ADMIN"|null }} opts
 * @returns {Promise<{ content: object[], page: number, size: number, totalElements: number, totalPages: number }>}
 */
export async function listUsers({ page = 0, size = 20, role = null } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (role) params.set("role", role);

  const res = await fetch(`${API_BASE}/admin/users?${params.toString()}`, {
    credentials: "include",
  });

  if (res.status === 401) {
    throw new AdminApiError("로그인이 필요합니다", 401);
  }
  if (res.status === 403) {
    throw new AdminApiError("관리자 권한이 필요합니다", 403);
  }
  if (!res.ok) {
    throw new AdminApiError(`유저 목록 조회 실패 (${res.status})`, res.status);
  }
  return res.json();
}

/**
 * 종합 대시보드 요약 (KPI 6종 + 최근 7일 가입 추이).
 * @returns {Promise<{ totalUsers: number, newUsersToday: number, adminCount: number,
 *   totalSaved: number, totalExpenses: number, totalAiInquiries: number,
 *   signupTrend: { date: string, count: number }[] }>}
 */
export async function getDashboardSummary() {
  const res = await fetch(`${API_BASE}/admin/dashboard/summary`, {
    credentials: "include",
  });

  if (res.status === 401) {
    throw new AdminApiError("로그인이 필요합니다", 401);
  }
  if (res.status === 403) {
    throw new AdminApiError("관리자 권한이 필요합니다", 403);
  }
  if (!res.ok) {
    throw new AdminApiError(`대시보드 조회 실패 (${res.status})`, res.status);
  }
  return res.json();
}

// 공통 GET — 401/403/기타 에러 분기 후 JSON 반환.
async function adminGet(path, label) {
  const res = await fetch(`${API_BASE}${path}`, { credentials: "include" });
  if (res.status === 401) throw new AdminApiError("로그인이 필요합니다", 401);
  if (res.status === 403) throw new AdminApiError("관리자 권한이 필요합니다", 403);
  if (!res.ok) throw new AdminApiError(`${label} 실패 (${res.status})`, res.status);
  return res.json();
}

/**
 * 지출 카테고리 통계 (총액 내림차순).
 * @returns {Promise<{ categoryId, categoryName, count, totalAmount, avgAmount, redCount, grayCount, greenCount }[]>}
 */
export function getCategoryStats() {
  return adminGet("/admin/category-stats", "카테고리 통계 조회");
}

/**
 * AI 대사 로그 (페이지네이션 + reason 필터).
 * @param {{ page?: number, size?: number, reason?: string|null }} opts
 */
export function getAiLogs({ page = 0, size = 20, reason = null } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (reason) params.set("reason", reason);
  return adminGet(`/admin/ai-logs?${params.toString()}`, "AI 로그 조회");
}

/** 합리성 신호 판정 룰 조회. @returns {Promise<{ zRed, zGreen, updatedAt }>} */
export function getRationalityRules() {
  return adminGet("/admin/rationality-rules", "합리성 룰 조회");
}

/** 제재 목록 (페이지네이션). */
export function getSanctions({ page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return adminGet(`/admin/sanctions?${params.toString()}`, "제재 목록 조회");
}

/** 제재 생성. type=SUSPENSION 이면 durationDays 필요(미입력 시 400). */
export async function createSanction({ userId, type, reason, durationDays }) {
  const res = await fetch(`${API_BASE}/admin/sanctions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ userId, type, reason, durationDays: durationDays ?? null }),
  });
  if (res.status === 401) throw new AdminApiError("로그인이 필요합니다", 401);
  if (res.status === 403) throw new AdminApiError("관리자 권한이 필요합니다", 403);
  if (res.status === 400 || res.status === 404) {
    const msg = await res
      .json()
      .then((b) => b.message)
      .catch(() => null);
    throw new AdminApiError(msg || "입력값을 확인해 주세요", res.status);
  }
  if (!res.ok) throw new AdminApiError(`제재 생성 실패 (${res.status})`, res.status);
  return res.json();
}

/** 제재 해제. */
export async function liftSanction(id) {
  const res = await fetch(`${API_BASE}/admin/sanctions/${id}/lift`, {
    method: "POST",
    credentials: "include",
  });
  if (res.status === 401) throw new AdminApiError("로그인이 필요합니다", 401);
  if (res.status === 403) throw new AdminApiError("관리자 권한이 필요합니다", 403);
  if (!res.ok) throw new AdminApiError(`제재 해제 실패 (${res.status})`, res.status);
  return res.json();
}

/** 합리성 룰 수정 (zGreen < zRed 필수, 위반 시 400). */
export async function updateRationalityRules({ zRed, zGreen }) {
  const res = await fetch(`${API_BASE}/admin/rationality-rules`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ zRed, zGreen }),
  });
  if (res.status === 401) throw new AdminApiError("로그인이 필요합니다", 401);
  if (res.status === 403) throw new AdminApiError("관리자 권한이 필요합니다", 403);
  if (res.status === 400) {
    const msg = await res
      .json()
      .then((b) => b.message)
      .catch(() => null);
    throw new AdminApiError(msg || "입력값을 확인해 주세요 (z_green < z_red)", 400);
  }
  if (!res.ok) throw new AdminApiError(`룰 저장 실패 (${res.status})`, res.status);
  return res.json();
}

// ───── AI 사용량 ─────

/**
 * Gemini 한도 상태와 「없어도 되는 AI」 스위치.
 * @returns {Promise<{ questionWording: boolean, dailyComment: boolean,
 *   models: { model: string, exhausted: boolean, availableAt: string|null }[] }>}
 */
export function getAiSettings() {
  return adminGet("/admin/ai-settings", "AI 사용량 조회");
}

/** 스위치 변경 — 보낸 칸만 바뀐다. 응답은 getAiSettings 와 같다. */
export function updateAiSettings({ questionWording, dailyComment }) {
  return adminSend("PUT", "/admin/ai-settings", { questionWording, dailyComment }, "AI 설정 저장");
}

// ───── 칭호 관리 ─────

// 공통 쓰기 — JSON 본문(선택). 400/404/409 는 서버 message 를 그대로 올린다.
async function adminSend(method, path, body, label) {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    credentials: "include",
    headers: body !== undefined ? { "Content-Type": "application/json" } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401) throw new AdminApiError("로그인이 필요합니다", 401);
  if (res.status === 403) throw new AdminApiError("관리자 권한이 필요합니다", 403);
  if (res.status === 400 || res.status === 404 || res.status === 409) {
    const msg = await res
      .json()
      .then((b) => b.message)
      .catch(() => null);
    throw new AdminApiError(msg || `${label} 실패 (${res.status})`, res.status);
  }
  if (!res.ok) throw new AdminApiError(`${label} 실패 (${res.status})`, res.status);
  if (res.status === 204) return null;
  return res.json();
}

/**
 * 업적 마스터 전체(비활성 포함, 정렬 순서대로). 펫 칭호(pet_titles)는 v1 에서 관리자 화면이 없다.
 * @returns {Promise<Array<{
 *   id:number, code:string, name:string, description:string,
 *   metricType:string, threshold:number, enabled:boolean, sortOrder:number,
 *   holderCount:number, unlocksThemeName:string|null, createdAt:string, updatedAt:string
 * }>>}
 */
export function listAdminTitles() {
  return adminGet("/admin/achievements", "업적 목록 조회");
}

/** 칭호 생성. code 중복이면 409. */
export function createTitle(body) {
  return adminSend("POST", "/admin/achievements", body, "업적 생성");
}

/** 칭호 수정. code 는 서버가 무시한다(생성 후 변경 불가). */
export function updateTitle(id, body) {
  return adminSend("PUT", `/admin/achievements/${id}`, body, "업적 수정");
}

/** 활성/비활성. 운영 중 칭호를 내리는 기본 경로 — 보유자 기록은 남는다. */
export function setTitleEnabled(id, enabled) {
  return adminSend("PATCH", `/admin/achievements/${id}/enabled?value=${enabled ? "true" : "false"}`, undefined, "업적 상태 변경");
}

/** 삭제. 보유자가 있거나 테마 해금 조건이면 409 (서버 message 에 이유). */
export function deleteTitle(id) {
  return adminSend("DELETE", `/admin/achievements/${id}`, undefined, "업적 삭제");
}

// ───── 관리자 본인 계정 도구 (/api/admin/me/**) ─────
// 사용자 화면에서 모든 종족·단계·구매·배치를 확인하기 위한 셀프 조작. AdminTools 컴포넌트가 쓴다.

/** 종족 목록. @returns {Promise<{id:number,name:string,tier:string,appearanceKey:string}[]>} */
export function listPetSpecies() {
  return adminGet("/admin/pet-species", "종족 목록 조회");
}

/** 활성 펫 종족·변종 변경. 활성 펫이 없으면 그 종족으로 새 펫 생성. */
export function adminSetMyPetAppearance(speciesId, variant = "NORMAL") {
  return adminSend(
    "PUT",
    `/admin/me/pet/appearance?speciesId=${speciesId}&variant=${encodeURIComponent(variant)}`,
    undefined,
    "펫 종족 변경",
  );
}

/** 활성 펫 단계 강제(INFANT|JUVENILE|ADULT). 내려가기도 허용. */
export function adminSetMyPetStage(stage) {
  return adminSend("PUT", `/admin/me/pet/stage?stage=${encodeURIComponent(stage)}`, undefined, "펫 단계 변경");
}

/** 활성 펫 레벨 강제(1~30). 30 이면 배웅 버튼이 열린다. */
export function adminSetMyPetLevel(level) {
  return adminSend("PUT", `/admin/me/pet/level?level=${encodeURIComponent(level)}`, undefined, "펫 레벨 변경");
}

/** 활성 펫 비우기(보상 0 배웅). */
export function adminClearMyPet() {
  return adminSend("DELETE", "/admin/me/pet", undefined, "펫 비우기");
}

/** 대상 사용자의 활성 펫을 지정한 성장 단계(또는 "GRADUATE" = 30레벨, 배웅 가능)까지 성장시킨다. */
export function growUserPet(userId, stage) {
  const query = stage === "GRADUATE" ? "level=30" : `stage=${encodeURIComponent(stage)}`;
  return adminSend(
    "POST",
    `/admin/users/${userId}/pet/grow?${query}`,
    undefined,
    "펫 성장",
  );
}
