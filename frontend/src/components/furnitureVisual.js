// 가구 외형·라벨 매핑 — 백엔드 FurnitureCatalog(category·statTarget) → 화면 표현.
// 이미지 에셋이 없는 카테고리는 이모지로 대신한다.
// 에셋이 추가되면 CATEGORY_IMAGE 에 카테고리 키만 등록하면 된다.
import bedImage from "../assets/furniture/bed.png";
import mirrorImage from "../assets/furniture/mirror-wall.png";
import corkBoardImage from "../assets/furniture/cork-board.png";
import bookshelfImage from "../assets/furniture/bookshelf.png";
import drawerChestImage from "../assets/furniture/drawer-chest.png";
import wallPictureImage from "../assets/furniture/wall-picture.png";
import vanityImage from "../assets/furniture/vanity.png";
import tentImage from "../assets/furniture/tent.png";
import picnicMatImage from "../assets/furniture/picnic-mat.png";
import campfireImage from "../assets/furniture/campfire.png";
import hammockImage from "../assets/furniture/hammock.png";
import parasolImage from "../assets/furniture/parasol.png";
import campingChairImage from "../assets/furniture/camping-chair.png";
import canopyBedImage from "../assets/furniture/canopy-bed.png";
import teaTableImage from "../assets/furniture/tea-table.png";
import fireplaceImage from "../assets/furniture/fireplace.png";
import rockingChairImage from "../assets/furniture/rocking-chair.png";
import treasureChestImage from "../assets/furniture/treasure-chest.png";
import sleepCapsuleImage from "../assets/furniture/sleep-capsule.png";
import telescopeImage from "../assets/furniture/telescope.png";
import swimTubeImage from "../assets/furniture/swim-tube.png";
import beachBallImage from "../assets/furniture/beach-ball.png";
import computerImage from "../assets/furniture/computer.png";
import leatherSofaImage from "../assets/furniture/leather-sofa.png";
import glassTableImage from "../assets/furniture/glass-table.png";
import lanternImage from "../assets/furniture/lantern.png";
import iceboxImage from "../assets/furniture/icebox.png";
import safeImage from "../assets/furniture/safe.png";
import deskImage from "../assets/furniture/desk.png";
import fridgeImage from "../assets/furniture/fridge.png";
import emptyDeskImage from "../assets/furniture/desk-empty.png";

const CATEGORY_IMAGE = {
  BED: bedImage,
  MIRROR: mirrorImage,
  BOARD: corkBoardImage,
  SHELF: bookshelfImage,
  DRAWER: drawerChestImage,
  PICTURE: wallPictureImage,
  VANITY: vanityImage,
  TENT: tentImage,
  PICNIC_MAT: picnicMatImage,
  CAMPFIRE: campfireImage,
  HAMMOCK: hammockImage,
  PARASOL: parasolImage,
  CAMP_CHAIR: campingChairImage,
  CANOPY_BED: canopyBedImage,
  TEA_TABLE: teaTableImage,
  FIREPLACE: fireplaceImage,
  ROCKING_CHAIR: rockingChairImage,
  TREASURE_CHEST: treasureChestImage,
  SLEEP_CAPSULE: sleepCapsuleImage,
  TELESCOPE: telescopeImage,
  SWIM_TUBE: swimTubeImage,
  BEACH_BALL: beachBallImage,
  COMPUTER: computerImage,
  LEATHER_SOFA: leatherSofaImage,
  GLASS_TABLE: glassTableImage,
  LANTERN: lanternImage,
  ICEBOX: iceboxImage,
  SAFE: safeImage,
  DESK: deskImage,
  FRIDGE: fridgeImage,
  EMPTY_DESK: emptyDeskImage,
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
  TENT: "⛺",
  PICNIC_MAT: "🧺",
  CAMPFIRE: "🔥",
  HAMMOCK: "🛌",
  PARASOL: "⛱️",
  CAMP_CHAIR: "🪑",
  CANOPY_BED: "🛏️",
  TEA_TABLE: "🫖",
  FIREPLACE: "🔥",
  ROCKING_CHAIR: "🪑",
  TREASURE_CHEST: "🧰",
  SLEEP_CAPSULE: "💤",
  TELESCOPE: "🔭",
  SWIM_TUBE: "🛟",
  BEACH_BALL: "🏐",
  LEATHER_SOFA: "🛋️",
  GLASS_TABLE: "☕",
  LANTERN: "🏮",
  ICEBOX: "🧊",
  SAFE: "🔒",
  DESK: "📝",
  FRIDGE: "❄️",
  EMPTY_DESK: "🪑",
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
  TENT: "텐트",
  PICNIC_MAT: "매트",
  CAMPFIRE: "모닥불",
  HAMMOCK: "해먹",
  PARASOL: "파라솔",
  CAMP_CHAIR: "캠핑 의자",
  CANOPY_BED: "침대",
  TEA_TABLE: "테이블",
  FIREPLACE: "벽난로",
  ROCKING_CHAIR: "의자",
  TREASURE_CHEST: "상자",
  SLEEP_CAPSULE: "캡슐",
  TELESCOPE: "망원경",
  SWIM_TUBE: "튜브",
  BEACH_BALL: "공",
  LEATHER_SOFA: "소파",
  GLASS_TABLE: "테이블",
  LANTERN: "랜턴",
  ICEBOX: "아이스박스",
  SAFE: "금고",
  DESK: "책상",
  FRIDGE: "냉장고",
  EMPTY_DESK: "책상",
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
  TENT: { x: 26, y: 62 },
  PICNIC_MAT: { x: 52, y: 84 },
  CAMPFIRE: { x: 74, y: 80 },
  HAMMOCK: { x: 22, y: 78 },
  PARASOL: { x: 62, y: 66 },
  CAMP_CHAIR: { x: 84, y: 78 },
  CANOPY_BED: { x: 22, y: 68 },
  TEA_TABLE: { x: 52, y: 80 },
  FIREPLACE: { x: 78, y: 60 },
  ROCKING_CHAIR: { x: 34, y: 80 },
  TREASURE_CHEST: { x: 62, y: 84 },
  SLEEP_CAPSULE: { x: 24, y: 70 },
  TELESCOPE: { x: 80, y: 66 },
  SWIM_TUBE: { x: 44, y: 86 },
  BEACH_BALL: { x: 66, y: 86 },
  LEATHER_SOFA: { x: 30, y: 74 },
  GLASS_TABLE: { x: 52, y: 84 },
  LANTERN: { x: 70, y: 86 },
  ICEBOX: { x: 40, y: 84 },
  SAFE: { x: 84, y: 76 },
  DESK: { x: 58, y: 74 },
  FRIDGE: { x: 88, y: 62 },
  EMPTY_DESK: { x: 60, y: 80 },
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
