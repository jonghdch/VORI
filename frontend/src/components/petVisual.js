// 펫 외형·라벨 매핑 — 백엔드 PetResponse(appearanceKey·stage·variant·tier) → 화면 표현.
// 이미지 에셋은 종족별·성장 단계별로 PET_IMAGE 에 등록한다. 파일명은 `<appearanceKey>-<단계번호>.png`
// (1 = 아기 INFANT, 2 = 청소년 JUVENILE, 3 = 성체 ADULT). 지금은 16종 모두 1·2차 이미지까지 있어서
// 성체(ADULT)는 가장 가까운 2차 이미지로 대신 표시한다. 3차 이미지가 생기면 ADULT 키만 추가하면 된다.
// 이미지가 없는 종족은 PET_EMOJI 로 대신한다.
import dragonStage1 from "../assets/pets/dragon-1.png";
import dragonStage2 from "../assets/pets/dragon-2.png";
import wolfStage1 from "../assets/pets/wolf-1.png";
import wolfStage2 from "../assets/pets/wolf-2.png";
import snakeStage1 from "../assets/pets/snake-1.png";
import snakeStage2 from "../assets/pets/snake-2.png";
import pandaStage1 from "../assets/pets/panda-1.png";
import pandaStage2 from "../assets/pets/panda-2.png";
import raccoonStage1 from "../assets/pets/raccoon-1.png";
import raccoonStage2 from "../assets/pets/raccoon-2.png";
import penguinStage1 from "../assets/pets/penguin-1.png";
import penguinStage2 from "../assets/pets/penguin-2.png";
import lionStage1 from "../assets/pets/lion-1.png";
import lionStage2 from "../assets/pets/lion-2.png";
import deerStage1 from "../assets/pets/deer-1.png";
import deerStage2 from "../assets/pets/deer-2.png";
import foxStage1 from "../assets/pets/fox-1.png";
import foxStage2 from "../assets/pets/fox-2.png";
import sheepStage1 from "../assets/pets/sheep-1.png";
import sheepStage2 from "../assets/pets/sheep-2.png";
import monkeyStage1 from "../assets/pets/monkey-1.png";
import monkeyStage2 from "../assets/pets/monkey-2.png";
import squirrelStage1 from "../assets/pets/squirrel-1.png";
import squirrelStage2 from "../assets/pets/squirrel-2.png";
import kittenStage1 from "../assets/pets/kitten-1.png";
import kittenStage2 from "../assets/pets/kitten-2.png";
import puppyStage1 from "../assets/pets/puppy-1.png";
import puppyStage2 from "../assets/pets/puppy-2.png";
import rabbitStage1 from "../assets/pets/rabbit-1.png";
import rabbitStage2 from "../assets/pets/rabbit-2.png";
import turtleStage1 from "../assets/pets/turtle-1.png";
import turtleStage2 from "../assets/pets/turtle-2.png";

const PET_IMAGE = {
  dragon: { INFANT: dragonStage1, JUVENILE: dragonStage2 },
  wolf: { INFANT: wolfStage1, JUVENILE: wolfStage2 },
  snake: { INFANT: snakeStage1, JUVENILE: snakeStage2 },
  panda: { INFANT: pandaStage1, JUVENILE: pandaStage2 },
  raccoon: { INFANT: raccoonStage1, JUVENILE: raccoonStage2 },
  penguin: { INFANT: penguinStage1, JUVENILE: penguinStage2 },
  lion: { INFANT: lionStage1, JUVENILE: lionStage2 },
  deer: { INFANT: deerStage1, JUVENILE: deerStage2 },
  fox: { INFANT: foxStage1, JUVENILE: foxStage2 },
  sheep: { INFANT: sheepStage1, JUVENILE: sheepStage2 },
  monkey: { INFANT: monkeyStage1, JUVENILE: monkeyStage2 },
  squirrel: { INFANT: squirrelStage1, JUVENILE: squirrelStage2 },
  kitten: { INFANT: kittenStage1, JUVENILE: kittenStage2 },
  puppy: { INFANT: puppyStage1, JUVENILE: puppyStage2 },
  rabbit: { INFANT: rabbitStage1, JUVENILE: rabbitStage2 },
  turtle: { INFANT: turtleStage1, JUVENILE: turtleStage2 },
};

const PET_EMOJI = {
  dragon: "🐉",
  wolf: "🐺",
  snake: "🐍",
  panda: "🐼",
  raccoon: "🦝",
  penguin: "🐧",
  lion: "🦁",
  deer: "🦌",
  fox: "🦊",
  sheep: "🐑",
  monkey: "🐵",
  squirrel: "🐿️",
  kitten: "🐱",
  puppy: "🐶",
  rabbit: "🐰",
  turtle: "🐢",
};

// 성장 단계 표시 이름 — 도감·펫·상점·스토리 모두 이 표를 쓴다 (도감의 "N차" 표기와 통일).
export const STAGE_LABEL = {
  INFANT: "1차",
  JUVENILE: "2차",
  ADULT: "3차",
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

// 레벨·진화 — 백엔드 PetLevel 과 같은 값. 레벨과 레벨 안 진행도는 서버가 PetResponse 로 계산해 준다
// (level·levelExp·levelExpNeeded·maxLevel). 여기 숫자는 안내 문구·다음 진화 표시에만 쓴다.
export const STAGE_LEVEL = { INFANT: 1, JUVENILE: 5, ADULT: 15 };
export const MAX_LEVEL = 30;

/** 다음 진화(또는 졸업)와 그 레벨. 졸업했으면 null. */
export function nextMilestone(level) {
  if (level < STAGE_LEVEL.JUVENILE) return { label: `${STAGE_LABEL.JUVENILE} 진화`, level: STAGE_LEVEL.JUVENILE };
  if (level < STAGE_LEVEL.ADULT) return { label: `${STAGE_LABEL.ADULT} 진화`, level: STAGE_LEVEL.ADULT };
  if (level < MAX_LEVEL) return { label: "졸업", level: MAX_LEVEL };
  return null;
}

/** 지금 레벨 안에서의 진행률(0~100). 만렙이면 100. */
export function levelProgressPct(pet) {
  if (!pet || !pet.levelExpNeeded) return pet ? 100 : 0;
  return Math.min(100, Math.round((pet.levelExp / pet.levelExpNeeded) * 100));
}

/** 화면에 부를 펫 이름 — 사용자가 지어 준 이름, 아직 없으면 종족 이름. */
export const petDisplayName = (pet) => pet?.name || pet?.speciesName || "펫";

/**
 * @param {string} appearanceKey
 * @param {string} [stage] INFANT | JUVENILE | ADULT. 해당 단계 이미지가 없으면 바로 아래 단계로 대신한다
 *   (성체 → 2차 → 1차).
 * @returns {{ image:string|null, emoji:string }}
 */
export function petVisual(appearanceKey, stage = "INFANT") {
  const images = PET_IMAGE[appearanceKey];
  return {
    image: images
      ? images[stage] ?? (stage === "ADULT" ? images.JUVENILE : undefined) ?? images.INFANT ?? null
      : null,
    emoji: PET_EMOJI[appearanceKey] ?? "🐾",
  };
}

/** 이미지가 있으면 <img>, 없으면 이모지. className 은 이미지에만 적용. */
export function PetArt({ appearanceKey, stage, name, className, emojiClassName }) {
  const { image, emoji } = petVisual(appearanceKey, stage);
  if (image) return <img src={image} alt={name || ""} className={className} />;
  return (
    <span className={emojiClassName} role="img" aria-label={name || "펫"}>
      {emoji}
    </span>
  );
}
