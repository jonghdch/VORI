// 본인 정보 API — GET /api/users/me.
// gameMoney·totalSaved 는 알 구매·지출 등록으로 계속 바뀌므로 세션의 user 객체가 아니라
// 이걸 다시 불러 화면을 갱신한다 (상점 코인 배지 등).
import { get } from "./http";

/**
 * @returns {Promise<{
 *   id:number, email:string, nickname:string, name:string|null, role:string,
 *   gameMoney:number, totalSaved:number, tutorialDone:boolean
 * }>}
 */
export const getMe = () => get("/users/me");
