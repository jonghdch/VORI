// 마이룸 테마 API 클라이언트.
//   GET /api/themes   테마별 해금 여부·배치 개수·세트 발동 여부 (발동 중인 것부터)
//
// 테마는 사용자가 "선택" 하는 게 아니다. 같은 테마 가구를 기준 개수만큼 **배치하면**
// 세트가 저절로 발동해 분양가 보너스가 붙는다. 화면에 필요한 건 선택 UI 가 아니라
// "지금 몇 개 놓였고 몇 개 남았는지" 를 보여주는 진행 표시다.
import { get } from "./http";

/**
 * 테마 한 개.
 *
 * @typedef {{
 *   id: number,
 *   name: string,                 // "우드"|"코지"|"스터디"
 *   setBonusPct: number,          // 세트 발동 시 분양가 가산 % (우드 8 / 코지 12 / 스터디 15)
 *   requiredCount: number,        // 발동에 필요한 배치 개수
 *   placedCount: number,          // 지금 배치된 개수 — 인벤토리에 쌓아둔 건 세지 않는다
 *   active: boolean,              // 지금 실제로 분양가에 보너스가 얹히고 있는지
 *   unlocked: boolean,            // false 면 이 테마 가구를 아직 살 수 없다
 *   unlockTitleName: string|null  // 잠금을 푸는 칭호 이름. null 이면 처음부터 열려 있다
 * }} Theme
 */

/**
 * 테마 현황 전체. 발동 중인 것이 앞에 온다.
 *
 * `placedCount / requiredCount` 를 그대로 쓰면 **"코지 2/3 — 하나만 더!"** 를 그릴 수 있다.
 * 이 안내를 위해 두 값을 따로 내려주는 것이니 굳이 계산해서 합치지 말 것.
 *
 * 주의: `active` 와 `unlocked` 는 다르다.
 *   unlocked=false            잠김 — 가구를 살 수조차 없다
 *   unlocked=true, active=false  살 수는 있는데 아직 덜 놓았다 (2/3)
 *   active=true               발동 중 — 분양가에 setBonusPct 가 붙는다
 *
 * @returns {Promise<Theme[]>}
 */
export const listThemes = () => get("/themes");

/**
 * 지금 발동 중인 테마들의 보너스 합계(%).
 *
 * 펫 분양가는 `스탯총합 × 10 × (1 + 배치 가구 releaseBonusPct 합 / 100)` 으로 계산되는데,
 * 분양 화면에서 "세트 보너스 +15%" 를 미리 보여주고 싶을 때 쓴다.
 * 실제 분양가는 서버가 계산하므로 이 값은 **안내용**이다 — 화면에서 분양가를 직접 계산하지 말 것.
 *
 * @param {Theme[]} themes listThemes() 결과
 * @returns {number}
 */
export const activeSetBonusPct = (themes) =>
  themes.filter((t) => t.active).reduce((sum, t) => sum + t.setBonusPct, 0);
