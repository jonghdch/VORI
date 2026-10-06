// 카테고리 자동 분류 API 클라이언트.
// 백엔드 POST /api/categories/categorize 호출 → 내 기록·상호 규칙·Gemini embedding 순으로 분류 후 leaf 반환.

import { query } from "./http";

// 서버 오류(5xx)나 네트워크 오류는 잠깐 뒤 한 번만 다시 시도한다. 4xx 는 다시 해도 같으므로 바로 포기.
const RETRY_DELAY_MS = 600;
const isRetryable = (err) => err?.status == null || err.status >= 500;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * @param {string} name - 사용자가 "내역" 필드에 입력한 텍스트
 * @returns {Promise<{leafId, leafName, parentId, parentName, score, source, askType, candidates} | null>}
 *          source: HISTORY(내가 전에 고른 것)·RULE(상호 규칙)·EMBEDDING·ASK(묻기 기본값)·FALLBACK(기타 생활)
 *          askType: 이름만으로 애매하면 WHAT(무엇을 샀나)·DINE_OR_DELIVERY(매장·포장/배달), 아니면 null
 *          candidates: 물어볼 때의 칩 [{leafId, label, leafName}], leafId 와 같은 칩이 기본 선택
 *          매칭 실패 시 (leafId=null), 또는 재시도까지 실패하면 null 반환.
 */
export async function categorizeRemote(name) {
  if (!name || !name.trim()) return null;
  const body = { name: name.trim() };
  let data;
  try {
    data = await query("/categories/categorize", body);
  } catch (err) {
    if (!isRetryable(err)) return null;
    await wait(RETRY_DELAY_MS);
    try {
      data = await query("/categories/categorize", body);
    } catch {
      return null;
    }
  }
  if (!data || data.leafId == null) return null;
  return data;
}
