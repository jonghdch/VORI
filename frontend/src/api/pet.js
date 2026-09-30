// 펫·알(가챠) API 클라이언트.
//   GET  /api/eggs/products      상점 상품(가격·확률)
//   GET  /api/eggs               보유 알 (기본 미개봉만)
//   POST /api/eggs/buy?grade=    알 구매
//   POST /api/eggs/{id}/open     가챠 개봉 → 펫 지급
//   GET  /api/pets/active        키우는 펫 (없으면 null)
//   GET  /api/pets               보유·분양 이력 전체
//   PUT  /api/pets/{id}/name     펫 이름 짓기 (1~10자)
//   POST /api/pets/{id}/release  성체 분양 → 게임머니 획득
//   POST /api/pets/active/interact  상호작용 1회 → 1% 확률로 매력 +1
import { get, post, put } from "./http";

/**
 * @typedef {{ grade:string, name:string, price:number, probabilities:Record<string,number> }} EggProduct
 * @typedef {{ id:number, gradeName:string, price:number, purchasedAt:string, openedAt:string|null, opened:boolean }} Egg
 * @typedef {{
 *   id:number, name:string|null, speciesId:number, speciesName:string, tier:string, appearanceKey:string,
 *   variant:"NORMAL"|"IRO"|"ALIEN", stage:"INFANT"|"JUVENILE"|"ADULT",
 *   statEnergy:number, statCharm:number, statIq:number, statEndurance:number, statTotal:number,
 *   hatchedAt:string, releasedAt:string|null, releaseValue:number|null
 * }} Pet
 */

/** @returns {Promise<EggProduct[]>} */
export const listEggProducts = () => get("/eggs/products");

/** @returns {Promise<Egg[]>} */
export const listMyEggs = (unopenedOnly = true) =>
  get(`/eggs?unopenedOnly=${unopenedOnly ? "true" : "false"}`);

/** @returns {Promise<Egg>} 400 = 코인 부족 */
export const buyEgg = (grade) => post(`/eggs/buy?grade=${encodeURIComponent(grade)}`);

/** @returns {Promise<{ eggId:number, pet:Pet, remainGameMoney:number }>} 409 = 이미 개봉 */
export const openEgg = (eggId) => post(`/eggs/${eggId}/open`);

/** @returns {Promise<Pet|null>} */
export const getActivePet = () => get("/pets/active");

/** @returns {Promise<Pet[]>} */
export const listPets = () => get("/pets");

/** @returns {Promise<Pet>} 400 = 이름 규칙 위반(1~10자), 409 = 이미 분양한 펫 */
export const namePet = (petId, name) => put(`/pets/${petId}/name`, { name });

/** @returns {Promise<Pet>} 400 = 성체 아님, 409 = 이미 분양 */
export const releasePet = (petId) => post(`/pets/${petId}/release`);

/**
 * 키우는 펫과 상호작용(쓰다듬기 등) 1회. 당첨 추첨은 서버가 한다.
 * @returns {Promise<{ charmUp:boolean, pet:Pet }>} 400 = 키우는 펫 없음
 */
export const interactWithPet = () => post("/pets/active/interact");
