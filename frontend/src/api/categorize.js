// 카테고리 자동 분류 API 클라이언트.
// 백엔드 POST /api/categories/categorize 호출 → 내 기록·상호 규칙·Gemini embedding 순으로 분류 후 leaf 반환.

import { API_BASE } from "./base";

/**
 * @param {string} name - 사용자가 "내역" 필드에 입력한 텍스트
 * @returns {Promise<{leafId, leafName, parentId, parentName, score, source} | null>}
 *          source: HISTORY(내가 전에 고른 것)·RULE(상호 규칙)·EMBEDDING·FALLBACK(기타 생활)
 *          매칭 실패 시 (leafId=null) null 반환.
 */
export async function categorizeRemote(name) {
  if (!name || !name.trim()) return null;
  try {
    const res = await fetch(`${API_BASE}/categories/categorize`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ name: name.trim() }),
    });
    if (!res.ok) return null;
    const data = await res.json();
    if (!data || data.leafId == null) return null;
    return data;
  } catch {
    return null;
  }
}
