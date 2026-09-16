// 가구 외형·라벨 매핑 — 백엔드 FurnitureCatalog(category·statTarget) → 화면 표현.
// 이미지 에셋은 bed.png 하나뿐이라 나머지 카테고리는 이모지로 대신한다.
// 에셋이 추가되면 CATEGORY_IMAGE 에 카테고리 키만 등록하면 된다.
import bedImage from "../assets/furniture/bed.png";

const CATEGORY_IMAGE = {
  BED: bedImage,
};

const CATEGORY_EMOJI = {
  BED: "🛏️",
  MIRROR: "🪞",
  VANITY: "💄",
  PICTURE: "🖼️",
  BOARD: "📌",
  SHELF: "📚",
  DRAWER: "🗄️",
  COMPUTER: "🖥️",
  WALLPAPER: "🧱",
  FLOOR: "🪵",
};

export const CATEGORY_LABEL = {
  BED: "침대",
  MIRROR: "거울",
  VANITY: "화장대",
  PICTURE: "액자",
  BOARD: "보드",
  SHELF: "책장",
  DRAWER: "서랍장",
  COMPUTER: "컴퓨터",
  WALLPAPER: "벽지",
  FLOOR: "바닥",
};

export const STAT_LABEL = {
  ENERGY: "에너지",
  CHARM: "매력",
  IQ: "지능",
  ENDURANCE: "지구력",
};

// 벽지·바닥은 방 전체에 깔리는 "면" — 드래그로 옮기는 물건이 아니다.
// 백엔드는 배치 좌표가 있어야 "배치됨"으로 치므로 고정 좌표로 배치한다.
export const SURFACE_POSITION = {
  WALLPAPER: { x: 50, y: 2 },
  FLOOR: { x: 50, y: 98 },
};

export const isSurface = (category) => category in SURFACE_POSITION;

// 인벤토리에서 "배치하기"를 눌렀을 때 처음 놓이는 자리(방 크기 대비 %). 카테고리별 기본 위치.
export const DEFAULT_POSITION = {
  BED: { x: 18, y: 74 },
  MIRROR: { x: 82, y: 36 },
  VANITY: { x: 80, y: 68 },
  PICTURE: { x: 50, y: 30 },
  BOARD: { x: 30, y: 32 },
  SHELF: { x: 64, y: 58 },
  DRAWER: { x: 40, y: 78 },
  COMPUTER: { x: 60, y: 76 },
};

/** @returns {{ image:string|null, emoji:string }} */
export function furnitureVisual(category) {
  return {
    image: CATEGORY_IMAGE[category] ?? null,
    emoji: CATEGORY_EMOJI[category] ?? "🪑",
  };
}

/** 이미지가 있으면 <img>, 없으면 이모지. className 은 이미지에만 적용. */
export function FurnitureArt({ category, name, className, emojiClassName }) {
  const { image, emoji } = furnitureVisual(category);
  if (image) return <img src={image} alt={name || ""} className={className} />;
  return (
    <span className={emojiClassName} role="img" aria-label={name || "가구"}>
      {emoji}
    </span>
  );
}
