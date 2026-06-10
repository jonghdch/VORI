// 사용자 스탯 API. 홈 대시보드 우측 위젯용.
import { API_BASE } from "./base";

export async function getMyStats() {
  try {
    const res = await fetch(`${API_BASE}/users/me/stats`, {
      method: "GET",
      credentials: "include",
    });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}
