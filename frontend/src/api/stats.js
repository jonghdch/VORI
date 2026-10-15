// 사용자 스탯 API. 홈 대시보드 우측 위젯용.
import { get } from "./http";

export async function getMyStats() {
  try {
    return await get("/users/me/stats");
  } catch {
    return null;
  }
}
