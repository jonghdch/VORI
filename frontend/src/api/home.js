// 홈 대시보드 요약 API. 스탯 + 기간별 지출 + 최근 지출 + 카테고리 분포.
const API_BASE = "http://localhost:8080/api";

/**
 * @returns {Promise<null | {
 *   stats: { energy:number, charm:number, iq:number, endurance:number, totalSaved:number },
 *   spending: { today:number, thisWeek:number, thisMonth:number },
 *   recentExpenses: { id:number, item:string, amount:number, categoryName:string, signalFinal:string|null, spentAt:string }[],
 *   categoryBreakdown: { categoryName:string, amount:number }[]
 * }>} 실패 시 null
 */
export async function getHomeSummary() {
  try {
    const res = await fetch(`${API_BASE}/users/me/home`, {
      method: "GET",
      credentials: "include",
    });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}
