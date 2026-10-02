// 스탯 아이템 외형 — 백엔드 아이템(statType) → 픽셀 이미지.
// 아이템은 올려 주는 능력치마다 하나씩이라 statType 으로 고르고, 모르는 값이면 이름으로 한 번 더 찾는다.
import vitaminImage from "../assets/items/vitamin.png";
import perfumeImage from "../assets/items/perfume.png";
import candyImage from "../assets/items/candy.png";
import drinkImage from "../assets/items/drink.png";

const STAT_IMAGE = {
  ENERGY: vitaminImage, // 활력 비타민
  CHARM: perfumeImage, // 매력 향수
  IQ: candyImage, // 집중 캔디
  ENDURANCE: drinkImage, // 튼튼 드링크
};

const NAME_IMAGE = [
  ["비타민", vitaminImage],
  ["향수", perfumeImage],
  ["캔디", candyImage],
  ["드링크", drinkImage],
];

/** @returns {string|null} 이미지 경로, 없으면 null */
export function itemImageFor(statType, name) {
  if (STAT_IMAGE[statType]) return STAT_IMAGE[statType];
  const hit = NAME_IMAGE.find(([key]) => (name || "").includes(key));
  return hit ? hit[1] : null;
}
