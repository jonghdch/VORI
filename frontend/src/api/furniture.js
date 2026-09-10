// 마이룸 가구 API 클라이언트.
//   GET   /api/furniture/products     상점 목록 (가격 오름차순, 잠긴 것 포함)
//   GET   /api/furniture              내 보유 가구 (배치된 것부터)
//   POST  /api/furniture/buy?item=    구매 → 인벤토리
//   PATCH /api/furniture/{id}/place   마이룸에 배치
//   PATCH /api/furniture/{id}/unplace 인벤토리로 회수
import { get, post, patch } from "./http";

/**
 * 상점 가구 한 개.
 *
 * **잠긴 가구도 숨기지 않고 locked:true 로 내려온다** — 해금 조건이 보여야 목표가 되기 때문.
 * 미획득 칭호를 진행률과 함께 내려주는 것과 같은 기조다. 잠긴 걸 사려 하면 서버가 403 으로 막는다.
 * 화면에서도 감추지 말고 흐리게 + `unlockTitleName` 을 같이 보여주는 걸 전제로 만든 응답이다.
 *
 * @typedef {{
 *   code: string,                  // "BOOKSHELF" — buyFurniture 에 넘기는 값
 *   name: string,                  // "책장"
 *   category: string,              // BED|WALLPAPER|FLOOR|MIRROR|VANITY|PICTURE|BOARD|SHELF|DRAWER|COMPUTER
 *   statTarget: string,            // ENERGY|CHARM|IQ|ENDURANCE
 *   releaseBonusPct: number,       // 분양가 가산 % (배치했을 때만 적용)
 *   price: number,                 // 코인
 *   themeName: string|null,        // "우드"|"코지"|"스터디" — null 이면 테마 없는 가구(벽지·바닥)
 *   themeSetBonusPct: number|null, // 그 테마의 세트 보너스 %
 *   locked: boolean,               // true 면 구매 불가
 *   unlockTitleName: string|null   // 잠금을 푸는 칭호 이름 ("절약 새싹" 등)
 * }} FurnitureProduct
 */

/**
 * 보유 가구 한 개.
 *
 * placed:false 는 인벤토리에 있다는 뜻이고, **분양가 보너스에도 테마 세트에도 반영되지 않는다.**
 * 사는 것만으로는 아무 효과가 없고 배치해야 효과가 생긴다 — 시연 각본 5번의 핵심.
 *
 * @typedef {{
 *   id: number,                // place/unplace 에 넘기는 값 (상품 code 가 아니다)
 *   name: string,
 *   category: string,
 *   statTarget: string,
 *   releaseBonusPct: number,
 *   price: number,
 *   positionX: number|null,    // 미배치면 null
 *   positionY: number|null,
 *   placed: boolean,
 *   acquiredAt: string         // ISO-8601
 * }} Furniture
 */

/**
 * 상점 목록. 가격 오름차순. **잠긴 가구까지 전부** 내려온다(locked 로 구분할 것).
 *
 * 로그인이 필요하다 — 잠금 여부가 사용자마다 다르기 때문(칭호 보유에 따라 갈린다).
 *
 * @returns {Promise<FurnitureProduct[]>}
 */
export const listFurnitureProducts = () => get("/furniture/products");

/** 내 보유 가구. 배치된 것이 앞에 온다. @returns {Promise<Furniture[]>} */
export const listMyFurniture = () => get("/furniture");

/**
 * 가구 구매. 산 직후에는 **인벤토리 상태**(placed:false)이므로 이어서 placeFurniture 를 불러야 한다.
 *
 * @param {string} code FurnitureProduct.code ("DESKTOP_PC" 등)
 * @returns {Promise<Furniture>} 403 = 잠긴 테마(칭호 미획득), 400 = 코인 부족
 */
export const buyFurniture = (code) =>
  post(`/furniture/buy?item=${encodeURIComponent(code)}`);

/**
 * 마이룸에 배치. 좌표는 **방 크기 대비 백분율**(0~100 정수)이다. 픽셀이 아니다.
 *
 * PetPage 의 `INITIAL_FURNITURE_POSITIONS` 가 이미 퍼센트를 쓰고 있어 그 단위를 그대로 받는다.
 * 비율이라 방 이미지나 화면 폭이 바뀌어도 배치가 안 깨진다 — 픽셀로 저장하면 창 크기마다
 * 가구가 다른 자리에 놓인다.
 *
 * @param {number} id Furniture.id
 * @param {number} positionX 0~100 (%)
 * @param {number} positionY 0~100 (%)
 * @returns {Promise<Furniture>} 409 = 이미 다른 가구가 놓인 자리, 403 = 남의 가구, 400 = 좌표 범위 밖
 */
export const placeFurniture = (id, positionX, positionY) =>
  patch(`/furniture/${id}/place`, { positionX, positionY });

/**
 * 인벤토리로 회수. 회수하면 분양가 보너스와 테마 세트에서 빠진다.
 *
 * @param {number} id Furniture.id
 * @returns {Promise<Furniture>} 403 = 남의 가구, 404 = 없는 가구
 */
export const unplaceFurniture = (id) => patch(`/furniture/${id}/unplace`);
