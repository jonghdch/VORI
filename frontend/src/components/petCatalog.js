// 펫 도감 목록 — 백엔드 PetSpeciesSeeder(pet_species 16종)와 동일하게 유지한다.
// appearanceKey 는 백엔드 pet_species.appearance_key 와 1:1 이라, 키우는 펫·다 키운 펫을
// 이 키로 맞춰 도감 카드에 표시한다. 종족을 추가·변경하면 시더와 이 목록, 그리고
// petVisual.js 의 이모지/이미지 매핑을 함께 고칠 것.
//
// 등급 표기는 백엔드 등급을 도감용 이름으로 옮긴 것:
//   C → 일반, B → 희귀, A → 에픽, S → 레전드
// 정렬은 등급 순(일반 → 레전드). 화면에서 그대로 쓴다.
export const PET_CATALOG = [
  { name: "고양이", tier: "COMMON", appearanceKey: "kitten" },
  { name: "강아지", tier: "COMMON", appearanceKey: "puppy" },
  { name: "토끼", tier: "COMMON", appearanceKey: "rabbit" },
  { name: "거북이", tier: "COMMON", appearanceKey: "turtle" },
  { name: "사슴", tier: "RARE", appearanceKey: "deer" },
  { name: "여우", tier: "RARE", appearanceKey: "fox" },
  { name: "양", tier: "RARE", appearanceKey: "sheep" },
  { name: "원숭이", tier: "RARE", appearanceKey: "monkey" },
  { name: "다람쥐", tier: "RARE", appearanceKey: "squirrel" },
  { name: "판다", tier: "EPIC", appearanceKey: "panda" },
  { name: "너구리", tier: "EPIC", appearanceKey: "raccoon" },
  { name: "펭귄", tier: "EPIC", appearanceKey: "penguin" },
  { name: "사자", tier: "EPIC", appearanceKey: "lion" },
  { name: "용", tier: "LEGEND", appearanceKey: "dragon" },
  { name: "늑대", tier: "LEGEND", appearanceKey: "wolf" },
  { name: "뱀", tier: "LEGEND", appearanceKey: "snake" },
];

// 도감 등급 — 낮은 등급부터 높은 등급 순. 필터 버튼 순서로도 쓴다.
export const DEX_TIERS = ["COMMON", "RARE", "EPIC", "LEGEND"];

export const DEX_TIER_LABEL = {
  COMMON: "일반",
  RARE: "희귀",
  EPIC: "에픽",
  LEGEND: "레전드",
};

// 성장 단계 순서 (1차 INFANT → 2차 JUVENILE → 3차 ADULT). 진화 단계 인덱스 계산에 쓴다.
export const STAGE_ORDER = ["INFANT", "JUVENILE", "ADULT"];
