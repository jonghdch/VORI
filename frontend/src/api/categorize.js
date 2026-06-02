// 카테고리 자동 분류 API 클라이언트.
// 백엔드 POST /api/categories/categorize 호출 → Gemini embedding 으로 분류 후 leaf 반환.

const API_BASE = "http://localhost:8080/api";

/**
 * @param {string} name - 사용자가 "내역" 필드에 입력한 텍스트
 * @returns {Promise<{leafId, leafName, parentId, parentName, score} | null>}
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
