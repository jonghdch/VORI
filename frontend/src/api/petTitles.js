// 펫 칭호 API 클라이언트. 칭호는 펫이 얻는다 — 펫을 분양하고 새 펫을 키우면 과제는 0부터 다시 시작한다.
//   GET /api/pet-titles                 키우는 펫의 칭호 과제와 진행도
//   PUT /api/pets/{id}/equipped-title   칭호 장착 / 장착 해제
import { get, put } from "./http";

/**
 * 칭호 과제 한 개. 딴 것과 못 딴 것이 같은 모양이다.
 *
 * @typedef {{
 *   code: string,
 *   name: string,
 *   description: string,
 *   metricType: "LEVEL"|"INTERACTIONS"|"AI_ANSWERS"|"CHARM_BONUS",
 *   hidden: boolean,          // 히든 칭호 — 못 딴 동안은 description "???", current·threshold 0, progressPct 만 의미 있다
 *   current: number,
 *   threshold: number,
 *   progressPct: number,
 *   acquired: boolean,
 *   awardId: number|null,     // 딴 칭호만. 장착할 때 보내는 값
 *   acquiredAt: string|null,
 *   equipped: boolean         // 지금 장착한 칭호인지
 * }} PetTitleItem
 */

/**
 * 키우는 펫의 칭호 과제. 펫이 없으면 petId 가 null 이고 과제는 0% 로 온다.
 * @returns {Promise<{ petId:number|null, petName:string|null, speciesName:string|null, titles:PetTitleItem[] }>}
 */
export const getPetTitleBoard = () => get("/pet-titles");

/**
 * 칭호 장착. awardId 가 null 이면 장착 해제. 응답은 갱신된 칭호 과제.
 * 400 = 이 펫이 딴 칭호가 아님, 409 = 분양한 펫.
 */
export const equipPetTitle = (petId, awardId) =>
  put(`/pets/${petId}/equipped-title`, { awardId });
