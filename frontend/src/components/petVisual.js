// 펫 외형·라벨 매핑 — 백엔드 PetResponse(appearanceKey·stage·variant·tier) → 화면 표현.
// 이미지 에셋은 아직 bori.png(강아지) 하나뿐이라 나머지 종족은 이모지로 대신한다.
// 에셋이 추가되면 PET_IMAGE 에 키만 등록하면 된다.
import boriImage from "../assets/pets/bori.png";

const PET_IMAGE = {
  puppy: boriImage,
};

const PET_EMOJI = {
  dragon: "🐉",
  lion: "🦁",
  snake: "🐍",
  fox: "🦊",
  deer: "🦌",
  penguin: "🐧",
  wolf: "🐺",
  turtle: "🐢",
  puppy: "🐶",
  kitten: "🐱",
  rabbit: "🐰",
  sheep: "🐑",
  frog: "🐸",
  squirrel: "🐿️",
  monkey: "🐵",
  panda: "🐼",
};

export const STAGE_LABEL = {
  INFANT: "아기",
  JUVENILE: "청소년",
  ADULT: "성체",
};

export const VARIANT_LABEL = {
  NORMAL: null,
  IRO: "이로치",
  ALIEN: "에일리언",
};

export const TIER_LABEL = {
  STARTER: "시작 펫",
  S: "S등급",
  A: "A등급",
  B: "B등급",
  C: "C등급",
};

// 진화 임계값 — 백엔드 Pet.minStatTotalFor 와 동일(200/300). 다음 단계까지 남은 양 표시용.
export const STAGE_THRESHOLD = { INFANT: 0, JUVENILE: 200, ADULT: 300 };

/** 다음 단계와 그 임계값. 성체면 null. */
export function nextStage(stage) {
  if (stage === "INFANT") return { stage: "JUVENILE", threshold: STAGE_THRESHOLD.JUVENILE };
  if (stage === "JUVENILE") return { stage: "ADULT", threshold: STAGE_THRESHOLD.ADULT };
  return null;
}

/** @returns {{ image:string|null, emoji:string }} */
export function petVisual(appearanceKey) {
  return {
    image: PET_IMAGE[appearanceKey] ?? null,
    emoji: PET_EMOJI[appearanceKey] ?? "🐾",
  };
}

/** 이미지가 있으면 <img>, 없으면 이모지. className 은 이미지에만 적용. */
export function PetArt({ appearanceKey, name, className, emojiClassName }) {
  const { image, emoji } = petVisual(appearanceKey);
  if (image) return <img src={image} alt={name || ""} className={className} />;
  return (
    <span className={emojiClassName} role="img" aria-label={name || "펫"}>
      {emoji}
    </span>
  );
}
