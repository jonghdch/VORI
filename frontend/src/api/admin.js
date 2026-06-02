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
