// 어드민 전용 API 클라이언트.
// 전 경로가 백엔드에서 hasRole("ADMIN") 으로 보호됨 → 일반 유저는 403.
// 세션 쿠키 인증이라 credentials: 'include' 필수.

const API_BASE = "http://localhost:8080/api";

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
