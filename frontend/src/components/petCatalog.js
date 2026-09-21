// 펫 도감 목록 — 백엔드 PetSpeciesSeeder(pet_species 16종)와 동일하게 유지한다.
// appearanceKey 는 백엔드 pet_species.appearance_key 와 1:1 이라, 키우는 펫·다 키운 펫을
// 이 키로 맞춰 도감 카드에 표시한다. 종족을 추가·변경하면 시더와 이 목록, 그리고
// petVisual.js 의 이모지/이미지 매핑을 함께 고칠 것.
//
// 등급 표기는 백엔드 등급을 도감용 이름으로 옮긴 것:
//   C → 일반, B → 희귀, A → 에픽, S → 레전드
// 정렬은 등급 순(일반 → 레전드). 화면에서 그대로 쓴다.
export const PET_CATALOG = [
  { name: "개구리", tier: "COMMON", appearanceKey: "frog" },
  { name: "다람쥐", tier: "COMMON", appearanceKey: "squirrel" },
  { name: "원숭이", tier: "COMMON", appearanceKey: "monkey" },
  { name: "팬더", tier: "COMMON", appearanceKey: "panda" },
  { name: "강아지", tier: "RARE", appearanceKey: "puppy" },
  { name: "고양이", tier: "RARE", appearanceKey: "kitten" },
  { name: "토끼", tier: "RARE", appearanceKey: "rabbit" },
  { name: "양", tier: "RARE", appearanceKey: "sheep" },
  { name: "사슴", tier: "EPIC", appearanceKey: "deer" },
  { name: "펭귄", tier: "EPIC", appearanceKey: "penguin" },
  { name: "늑대", tier: "EPIC", appearanceKey: "wolf" },
  { name: "거북이", tier: "EPIC", appearanceKey: "turtle" },
  { name: "용", tier: "LEGEND", appearanceKey: "dragon" },
  { name: "사자", tier: "LEGEND", appearanceKey: "lion" },
  { name: "뱀", tier: "LEGEND", appearanceKey: "snake" },
  { name: "여우", tier: "LEGEND", appearanceKey: "fox" },
];

// 도감 등급 — 낮은 등급부터 높은 등급 순. 필터 버튼 순서로도 쓴다.
export const DEX_TIERS = ["COMMON", "RARE", "EPIC", "LEGEND"];

export const DEX_TIER_LABEL = {
  COMMON: "일반",
  RARE: "희귀",
  EPIC: "에픽",
  LEGEND: "레전드",
};

// 성장 단계 순서 (아기 → 청소년 → 성체). 진화 단계 인덱스 계산에 쓴다.
export const STAGE_ORDER = ["INFANT", "JUVENILE", "ADULT"];
