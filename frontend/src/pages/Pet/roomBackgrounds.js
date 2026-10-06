import roomDefaultImage from "../../assets/backgrounds/room-default.png";
import roomWoodImage from "../../assets/backgrounds/room-wood.png";
import roomMintImage from "../../assets/backgrounds/room-mint.png";
import roomEveningImage from "../../assets/backgrounds/room-evening.png";
import roomModernImage from "../../assets/backgrounds/room-modern.png";
import roomPrincessImage from "../../assets/backgrounds/room-princess.png";
import roomOceanImage from "../../assets/backgrounds/room-ocean.png";
import roomForestImage from "../../assets/backgrounds/room-forest.png";
import roomSnowImage from "../../assets/backgrounds/room-snow.png";
import roomDesertImage from "../../assets/backgrounds/room-desert.png";
import roomSpaceImage from "../../assets/backgrounds/room-space.png";
import roomDragonImage from "../../assets/backgrounds/room-dragon.png";
import roomAtticImage from "../../assets/backgrounds/room-attic.png";
import outdoorForestImage from "../../assets/backgrounds/outdoor-forest.png";
import outdoorOceanImage from "../../assets/backgrounds/outdoor-ocean.png";

// 브라우저에만 저장하는 마이룸 배경 카탈로그. 백엔드 가구의 벽지·바닥과는 별개다.
export const ROOM_BACKGROUNDS = [
  ["default", "1번 슬롯", "햇살 거실", roomDefaultImage],
  ["wood", "2번 슬롯", "원목 오두막", roomWoodImage],
  ["mint", "3번 슬롯", "민트 침실", roomMintImage],
  ["evening", "4번 슬롯", "달빛 거실", roomEveningImage],
  ["modern", "5번 슬롯", "시티 라운지", roomModernImage],
  ["princess", "6번 슬롯", "공주 침실", roomPrincessImage],
  ["ocean", "7번 슬롯", "해변 별장", roomOceanImage],
  ["forest", "8번 슬롯", "숲속 은신처", roomForestImage],
  ["snow", "9번 슬롯", "설산 산장", roomSnowImage],
  ["desert", "10번 슬롯", "사막 오아시스", roomDesertImage],
  ["space", "11번 슬롯", "우주 정거장", roomSpaceImage],
  ["dragon", "12번 슬롯", "용의 둥지", roomDragonImage],
  ["attic", "13번 슬롯", "별밤 다락", roomAtticImage],
  ["outdoor-forest", "14번 슬롯", "초록 평야", outdoorForestImage],
  ["outdoor-ocean", "15번 슬롯", "여름 해변", outdoorOceanImage],
].map(([id, slot, name, image]) => ({
  id,
  slot,
  name,
  image,
  owned: true,
  className: "pet-room-bg--image",
}));
