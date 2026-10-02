import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import {
  PetArt,
  petDisplayName,
  levelProgressPct,
  nextMilestone,
  MAX_LEVEL,
} from "../../components/petVisual";
import { getActivePet, interactWithPet, listPets, releasePet } from "../../api/pet";
import { PET_CHANGED_EVENT } from "../../api/user";
import { listMyFurniture, placeFurniture as apiPlaceFurniture, unplaceFurniture } from "../../api/furniture";
import { listThemes } from "../../api/theme";
import { listStatItems, consumeStatItem } from "../../api/attendance";
import {
  CATEGORY_LABEL,
  DEFAULT_POSITION,
  FurnitureArt,
  SURFACE_POSITION,
  furnitureVisual,
  STAT_LABEL,
  isSurface,
} from "../../components/furnitureVisual";
import { PetActionMenu, PetReaction, REACTION_MS, pickLine } from "./PetInteraction";
import PetHistoryPanel from "./PetHistoryPanel";
import PetChatPanel from "./PetChatPanel";
import roomDefaultImage from "../../assets/backgrounds/room-default.png";
import roomWoodImage from "../../assets/backgrounds/room-wood.png";
import roomMintImage from "../../assets/backgrounds/room-mint.png";
import roomEveningImage from "../../assets/backgrounds/room-evening.png";
import "../Home/HomeDashboard.css";
import "./PetPage.css";

// 펫은 GET /api/pets/active, 가구는 GET /api/furniture 로 받는다.
// 방 색상(BACKGROUNDS)은 백엔드에 대응 개념이 없어 브라우저(localStorage)에만 저장하는 개인 취향값.
// 벽지·바닥은 백엔드 가구(WALLPAPER/FLOOR)라 보유 가구 쪽에서 다룬다.
const PET_ACCENT = "#f2c27b";
const BACKGROUND_STORAGE_KEY = "vori.myroom.background";

const BACKGROUNDS = [
  {
    id: "default",
    slot: "1번 슬롯",
    name: "기본 방",
    owned: true,
    className: "pet-room-bg--image",
    image: roomDefaultImage,
  },
  {
    id: "wood",
    slot: "2번 슬롯",
    name: "나무 방",
    owned: true,
    className: "pet-room-bg--image",
    image: roomWoodImage,
  },
  {
    id: "mint",
    slot: "3번 슬롯",
    name: "민트 방",
    owned: true,
    className: "pet-room-bg--image",
    image: roomMintImage,
  },
  {
    id: "evening",
    slot: "4번 슬롯",
    name: "저녁 방",
    owned: true,
    className: "pet-room-bg--image",
    image: roomEveningImage,
  },
];

const INITIAL_PET_POSITION = { x: 50, y: 62 };

// 오른쪽 카드 하나에서 탭으로 전환해 보는 섹션들
const PANEL_TABS = [
  { id: "pet", label: "펫 상태" },
  { id: "chat", label: "대화방" },
  { id: "background", label: "방 색상" },
  { id: "furniture", label: "보유 가구" },
  { id: "items", label: "아이템" },
];
// 탭 줄 오른쪽 끝 "내역 보기" — 펫 이력·아이템 내역을 한곳에서 (PetHistoryPanel)
const HISTORY_TAB = "history";

function readStoredBackground() {
  try {
    return localStorage.getItem(BACKGROUND_STORAGE_KEY) || "default";
  } catch {
    return "default";
  }
}

// 스탯 4종 표시 메타 — 값은 펫 본인의 스탯(PetResponse.stat*). 100 을 바 만점으로 본다.
const STAT_META = [
  { key: "statEnergy", label: "에너지", color: "var(--home-bar-green)" },
  { key: "statCharm", label: "매력", color: "var(--home-bar-red)" },
  { key: "statIq", label: "지능", color: "var(--home-bar-orange)" },
  { key: "statEndurance", label: "지구력", color: "var(--home-bar-blue)" },
];

const formatDate = (iso) => (iso ? iso.slice(0, 10).replaceAll("-", ". ") : "");
const coin = (n) => `${(n ?? 0).toLocaleString("ko-KR")} 코인`;

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

// 방 카드 아래에 한 문장씩 돌아가며 보여주는 팁
const ROOM_TIPS = [
  "펫과 가구를 드래그해서 원하는 위치에 배치해요.",
  "펫을 우클릭하면 쓰다듬거나 칭찬할 수 있어요.",
  "가구는 더블클릭하면 인벤토리로 회수돼요.",
  "배치한 가구만 분양가 보너스에 반영돼요.",
];

function pickTipIndex(previous) {
  const next = Math.floor(Math.random() * (ROOM_TIPS.length - 1));
  // 직전 팁은 건너뛴다 — 같은 문장이 연달아 나오면 멈춘 것처럼 보인다
  return previous === undefined || next < previous ? next : next + 1;
}

/**
 * 팁 한 문장이 나타났다 사라지고, 사라지면 다른 팁으로 바뀐다. 한 번의 나타남~사라짐이
 * CSS 애니메이션(pet-room-tip) 한 번이고, 끝나는 시점에 다음 문장을 고른다.
 * 마우스를 올리거나 초점을 두면 멈춰서 끝까지 읽을 수 있다.
 */
function RoomTips() {
  const [tipIndex, setTipIndex] = useState(() =>
    Math.floor(Math.random() * ROOM_TIPS.length),
  );
  return (
    <div className="pet-room-help" role="note" tabIndex={0} aria-label="마이룸 팁">
      <span className="pet-room-help-label" aria-hidden>
        팁
      </span>
      <p
        key={tipIndex}
        className="pet-room-help-text"
        onAnimationEnd={() => setTipIndex(pickTipIndex(tipIndex))}
      >
        {ROOM_TIPS[tipIndex]}
      </p>
    </div>
  );
}

function PetPage({ user, onLogout }) {
  const roomStageRef = useRef(null);
  const furniturePointerRef = useRef({ id: null, time: 0, moved: false, x: 0, y: 0 });
  const nickname = user?.nickname || "사용자";

  const navigate = useNavigate();

  // 키우는 펫 + 보유·분양 이력. pet === null 이면 "펫 없음"(신규 가입 직후·분양 직후).
  const [pet, setPet] = useState(null);
  const [petHistory, setPetHistory] = useState([]);
  const [petLoading, setPetLoading] = useState(true);
  const [petError, setPetError] = useState(null);
  const [releasing, setReleasing] = useState(false);
  const [notice, setNotice] = useState(null); // { kind:"ok"|"err", text }

  const loadPets = useCallback(async () => {
    const [active, all] = await Promise.all([getActivePet(), listPets()]);
    setPet(active);
    setPetHistory(all);
  }, []);

  useEffect(() => {
    let alive = true;
    loadPets()
      .catch((e) => {
        if (!alive) return;
        if (e.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setPetError(e.message || "펫 정보를 불러오지 못했어요");
      })
      .finally(() => {
        if (alive) setPetLoading(false);
      });
    // 관리자 도구가 펫을 바꾸면 다시 읽는다
    const onPetChanged = () => loadPets().catch(() => {});
    window.addEventListener(PET_CHANGED_EVENT, onPetChanged);
    return () => {
      alive = false;
      window.removeEventListener(PET_CHANGED_EVENT, onPetChanged);
    };
  }, [loadPets, navigate]);

  const handleRelease = async () => {
    if (!pet) return;
    const ok = window.confirm(
      `${petDisplayName(pet)}을(를) 분양할까요? 분양하면 더 이상 키울 수 없고, 스탯에 따라 코인을 받아요.`,
    );
    if (!ok) return;
    setReleasing(true);
    setNotice(null);
    try {
      const released = await releasePet(pet.id);
      await loadPets();
      setNotice({
        kind: "ok",
        text: `${petDisplayName(released)}을(를) 분양하고 ${coin(released.releaseValue)}을 받았어요.`,
      });
    } catch (e) {
      setNotice({
        kind: "err",
        text:
          e.status === 400
            ? e.message || `${MAX_LEVEL}레벨을 달성한 펫만 분양할 수 있어요.`
            : e.status === 409
              ? "이미 분양한 펫이에요."
              : e.message,
      });
    } finally {
      setReleasing(false);
    }
  };

  const [selectedBackgroundId, setSelectedBackgroundId] = useState(readStoredBackground);
  const chooseBackground = (id) => {
    setSelectedBackgroundId(id);
    try {
      localStorage.setItem(BACKGROUND_STORAGE_KEY, id);
    } catch {}
  };

  const [activeTab, setActiveTab] = useState("pet");
  const [petPosition, setPetPosition] = useState(INITIAL_PET_POSITION);
  const [dragTarget, setDragTarget] = useState(null);

  // 보유 가구(서버) + 드래그 중 임시 좌표(로컬). 드래그가 끝나면 서버에 저장하고 임시 좌표를 지운다.
  const [furniture, setFurniture] = useState([]);
  const [furnitureLoading, setFurnitureLoading] = useState(true);
  const [dragPositions, setDragPositions] = useState({}); // { [id]: {x,y} }
  const [furnitureBusy, setFurnitureBusy] = useState(null); // 가구 id
  const [statItems, setStatItems] = useState([]);
  const [itemBusy, setItemBusy] = useState(false);

  const loadItems = useCallback(async () => {
    setStatItems((await listStatItems()) || []);
  }, []);

  useEffect(() => {
    loadItems().catch((e) => {
      if (e.status !== 401) setNotice({ kind: "err", text: e.message });
    });
  }, [loadItems]);

  const handleUseItem = async (item) => {
    if (itemBusy) return;
    setItemBusy(true);
    try {
      await consumeStatItem(item.id);
      setStatItems((items) => items.filter((current) => current.id !== item.id));
      await loadPets();
      setNotice({ kind: "ok", text: `${item.name} 사용! ${STAT_LABEL[item.statType]} +${item.statDelta}` });
    } catch (e) { setNotice({ kind: "err", text: e.message }); }
    finally { setItemBusy(false); }
  };

  const loadFurniture = useCallback(async () => {
    setFurniture(await listMyFurniture());
  }, []);

  useEffect(() => {
    let alive = true;
    loadFurniture()
      .catch((e) => {
        if (alive && e.status !== 401) setNotice({ kind: "err", text: e.message });
      })
      .finally(() => {
        if (alive) setFurnitureLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [loadFurniture]);

  // 테마 세트 현황 — 발동 여부는 서버가 판단한다(GET /api/themes). 배치·회수로 놓인 가구가
  // 바뀔 때만 다시 받는다. 드래그로 자리만 옮기는 건 개수가 그대로라 부르지 않는다.
  const [themes, setThemes] = useState([]);
  const placedKey = furniture
    .filter((f) => f.placed)
    .map((f) => f.id)
    .join(",");

  useEffect(() => {
    if (furnitureLoading) return;
    let alive = true;
    listThemes()
      .then((list) => {
        if (alive) setThemes(list);
      })
      .catch((e) => {
        if (alive && e.status !== 401) setNotice({ kind: "err", text: e.message });
      });
    return () => {
      alive = false;
    };
  }, [furnitureLoading, placedKey]);

  // 화면용 펫 표현 — 펫이 없으면 방은 비워 두고 안내만 보여준다.
  const selectedPet = pet
    ? {
        id: String(pet.id),
        name: petDisplayName(pet),
        appearanceKey: pet.appearanceKey,
        stage: pet.stage,
        color: PET_ACCENT,
      }
    : null;
  const milestone = pet ? nextMilestone(pet.level) : null;
  const graduated = pet ? pet.level >= pet.maxLevel : false;
  const selectedBackground =
    BACKGROUNDS.find((background) => background.id === selectedBackgroundId) ??
    BACKGROUNDS[0];

  // 방에 그리는 가구: 배치된 것 중 벽지·바닥(면)은 칩으로, 나머지는 드래그 가능한 물건으로.
  const placedFurniture = useMemo(
    () => furniture.filter((f) => f.placed && !isSurface(f.category)),
    [furniture],
  );
  const roomBonus = useMemo(() => {
    const stat = { ENERGY: 0, CHARM: 0, IQ: 0, ENDURANCE: 0 };
    let release = 0;
    furniture.filter((item) => item.placed).forEach((item) => {
      const pct = Number(item.releaseBonusPct || 0);
      stat[item.statTarget] += pct;
      release += pct;
    });
    // 발동한 테마 세트 보너스도 실제 분양가 계산에 포함되므로 총 분양가에 합산한다.
    release += themes.filter((theme) => theme.active).reduce((sum, theme) => sum + Number(theme.setBonusPct || 0), 0);
    return { stat, release };
  }, [furniture, themes]);
  const positionOf = (item) =>
    dragPositions[item.id] ?? { x: item.positionX ?? 50, y: item.positionY ?? 72 };

  // 같은 정수 좌표엔 가구 하나만 놓인다(서버 409). 기본 자리가 차 있으면 옆으로 민다.
  const findFreeSpot = (start) => {
    const taken = new Set(furniture.filter((f) => f.placed).map((f) => `${f.positionX},${f.positionY}`));
    let { x, y } = start;
    for (let i = 0; i < 12 && taken.has(`${x},${y}`); i++) {
      x = x + 7 > 96 ? 8 : x + 7;
      if (x === 8) y = Math.min(94, y + 6);
    }
    return { x, y };
  };

  const savePlacement = async (item, point) => {
    const x = clamp(Math.round(point.x), 0, 100);
    const y = clamp(Math.round(point.y), 0, 100);
    setFurnitureBusy(item.id);
    try {
      const saved = await apiPlaceFurniture(item.id, x, y);
      setFurniture((list) => list.map((f) => (f.id === saved.id ? saved : f)));
    } catch (e) {
      // 409(자리 겹침)·400(범위 밖) 등 — 서버 좌표로 되돌린다
      setNotice({ kind: "err", text: e.message });
    } finally {
      setDragPositions((pos) => {
        const next = { ...pos };
        delete next[item.id];
        return next;
      });
      setFurnitureBusy(null);
    }
  };

  const placeFurniture = (item) => {
    if (item.placed || furnitureBusy) return;
    const start = isSurface(item.category)
      ? SURFACE_POSITION[item.category]
      : DEFAULT_POSITION[item.category] ?? { x: 50, y: 72 };
    savePlacement(item, isSurface(item.category) ? start : findFreeSpot(start));
  };

  const removeFurniture = async (item) => {
    if (!item.placed || furnitureBusy) return;
    setFurnitureBusy(item.id);
    try {
      const saved = await unplaceFurniture(item.id);
      setFurniture((list) => list.map((f) => (f.id === saved.id ? saved : f)));
    } catch (e) {
      setNotice({ kind: "err", text: e.message });
    } finally {
      setFurnitureBusy(null);
    }
  };

  const getRoomPoint = (event) => {
    const rect = roomStageRef.current?.getBoundingClientRect();
    if (!rect) return null;

    return {
      x: clamp(((event.clientX - rect.left) / rect.width) * 100, 4, 96),
      y: clamp(((event.clientY - rect.top) / rect.height) * 100, 6, 94),
    };
  };

  const updateDragPosition = (target, event) => {
    const point = getRoomPoint(event);
    if (!point) return;

    if (target.type === "pet") {
      setPetPosition(point);
      return;
    }

    setDragPositions((positions) => ({
      ...positions,
      [target.id]: point,
    }));
  };

  const startDrag = (target, event) => {
    if (event.button !== 0) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture?.(event.pointerId);
    furniturePointerRef.current = {
      id: target.type === "furniture" ? target.id : null,
      time: Date.now(),
      moved: false,
      x: event.clientX,
      y: event.clientY,
    };
    setDragTarget(target);
  };

  const continueDrag = (target, event) => {
    if (
      !dragTarget ||
      dragTarget.type !== target.type ||
      dragTarget.id !== target.id
    ) {
      return;
    }
    const pointer = furniturePointerRef.current;
    if (target.type === "furniture" && Math.hypot(event.clientX - pointer.x, event.clientY - pointer.y) > 4) {
      pointer.moved = true;
    }
    updateDragPosition(target, event);
  };

  const endDrag = (event) => {
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture?.(event.pointerId);
    }
    // 가구 드래그가 끝나면 마지막 좌표를 서버에 저장한다(펫 위치는 로컬 전용)
    const wasFurnitureDrag = dragTarget?.type === "furniture" && furniturePointerRef.current.moved;
    if (wasFurnitureDrag) {
      const item = furniture.find((f) => f.id === dragTarget.id);
      const point = dragPositions[dragTarget.id];
      if (item && point) savePlacement(item, point);
    }
    if (wasFurnitureDrag || dragTarget?.type !== "furniture") {
      furniturePointerRef.current = { id: null, time: 0, moved: false, x: 0, y: 0 };
    }
    setDragTarget(null);
  };

  // 펫 우클릭 메뉴(쓰다듬기·칭찬하기 등)와 그 반응. 반응은 바로 보여주고, 매력 보너스(1%)는
  // 서버가 추첨해서 알려주면 그때 덧붙인다.
  const petRef = useRef(null);
  const petIconRef = useRef(null);
  const reactionSeqRef = useRef(0);
  const [petMenu, setPetMenu] = useState(null); // { x, y } 화면 좌표
  const [reaction, setReaction] = useState(null); // { id, actionId, line, particle, charmUp? }

  const openPetMenu = (event) => {
    event.preventDefault();
    setDragTarget(null); // macOS ctrl+클릭은 드래그 시작과 우클릭이 같이 들어온다
    // 키보드(메뉴 키·Shift+F10·Enter)로 열면 포인터 좌표가 없으니 펫 가운데에 띄운다
    const hasPointer = event.clientX > 0 || event.clientY > 0;
    const rect = event.currentTarget.getBoundingClientRect();
    setPetMenu(
      hasPointer
        ? { x: event.clientX, y: event.clientY }
        : { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 },
    );
  };

  const closePetMenu = useCallback((restoreFocus) => {
    setPetMenu(null);
    if (restoreFocus) petRef.current?.focus();
  }, []);

  const handlePetAction = (action) => {
    closePetMenu(true);
    reactionSeqRef.current += 1;
    const reactionId = reactionSeqRef.current;
    setReaction((previous) => ({
      id: reactionId,
      actionId: action.id,
      line: pickLine(action, previous?.line),
      particle: action.particle,
    }));
    const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (!reduceMotion) {
      petIconRef.current?.animate?.(action.motion.keyframes, {
        duration: action.motion.duration,
        easing: "ease-in-out",
      });
    }

    interactWithPet()
      .then((result) => {
        if (!result) return;
        const messages = [];
        if (result.charmUp) {
          setPet(result.pet);
          // 그사이 다른 반응으로 넘어갔으면 말풍선 옆 표시는 건너뛰고 안내 문구만 남긴다
          setReaction((current) =>
            current?.id === reactionId ? { ...current, charmUp: true } : current,
          );
          messages.push(`${petDisplayName(result.pet)}의 매력이 1 올랐어요!`);
        }
        // 상호작용으로 펫이 얻은 칭호 — 히든 칭호는 목록에 없던 것이라 여기서 알려줘야 한다
        const newTitles = result.newTitles ?? [];
        if (newTitles.length > 0) setPet(result.pet);
        for (const title of newTitles) {
          messages.push(
            `${petDisplayName(result.pet)}이(가) ${title.hidden ? "히든 칭호" : "칭호"} 「${title.name}」를 얻었어요! 도감 칭호 탭에서 장착할 수 있어요.`,
          );
        }
        if (messages.length > 0) setNotice({ kind: "ok", text: messages.join(" ") });
      })
      .catch((e) => {
        if (e.status !== 401) setNotice({ kind: "err", text: e.message });
      });
  };

  // 매력 보너스 표시가 뒤늦게 붙어도 사라지는 시각은 그대로 두려고 id 에만 묶는다
  const reactionId = reaction?.id;
  useEffect(() => {
    if (reactionId == null) return undefined;
    const timer = setTimeout(() => setReaction(null), REACTION_MS);
    return () => clearTimeout(timer);
  }, [reactionId]);

  const handleFurniturePointerDown = (item, event) => {
    if (event.button !== 0) return;
    const previous = furniturePointerRef.current;
    const isDoubleClick = previous.id === item.id && Date.now() - previous.time < 400 && !previous.moved;
    if (isDoubleClick) {
      event.preventDefault();
      furniturePointerRef.current = { id: null, time: 0, moved: false, x: 0, y: 0 };
      removeFurniture(item);
      return;
    }
    startDrag({ type: "furniture", id: item.id }, event);
  };

  return (
    <AppShell
      activeTop="raise"
      activeSide="raise"
      user={user}
      onLogout={onLogout}
    >
      <main className="home-main pet-main">

        <div className="pet-myroom-layout">
          {/* 왼쪽: 방 */}
          <div className="pet-myroom-main">
            <section
              className={`home-card pet-room-card ${selectedBackground.className} ${
                selectedBackground.image ? "pet-room-card--image" : ""
              }`}
              style={
                selectedBackground.image
                  ? { backgroundImage: `url(${selectedBackground.image})` }
                  : undefined
              }
            >
              <div className="pet-room-top">
                {/* 방 제목 — 펫이 있으면 "(펫 이름)의 방" */}
                <h1 className="pet-title">
                  <span className="pet-title-badge" aria-hidden>
                    🏠
                  </span>
                  <span className="pet-title-text">
                    {selectedPet ? `${selectedPet.name}의 방` : `${nickname}님의 방`}
                  </span>
                </h1>
                <div className="pet-room-chips">
                  <ul className="pet-surface-chips pet-room-bonus-chips" aria-label="배치 가구 보너스">
                    <li className="pet-room-bonus-chip--release">분양가 +{roomBonus.release}%</li>
                    {Object.entries(roomBonus.stat).filter(([, pct]) => pct > 0).map(([stat, pct]) => (
                      <li key={stat}>{STAT_LABEL[stat]} +{pct}%</li>
                    ))}
                    {roomBonus.release === 0 && <li>배치 가구 보너스 없음</li>}
                  </ul>
                </div>
              </div>

              <div
                ref={roomStageRef}
                className={`pet-room-stage ${
                  selectedBackground.image ? "pet-room-stage--image" : ""
                }`}
                aria-label="펫 방 미리보기"
              >
                {!selectedBackground.image && (
                  <>
                    <div className="pet-room-window" aria-hidden />
                    <div className="pet-room-floor" aria-hidden />
                  </>
                )}

                {placedFurniture.map((item) => {
                  const pos = positionOf(item);
                  return (
                    <button
                      key={item.id}
                      type="button"
                      className={`pet-placed-item ${
                        dragTarget?.type === "furniture" && dragTarget.id === item.id
                          ? "is-dragging"
                          : ""
                      } ${
                        // 이미지가 있는 가구는 침대처럼 방 크기에 맞춘 그림으로 놓는다.
                        // 종류별 클래스는 이미지·이모지 모두에 붙여 크기를 따로 정할 수 있게 한다.
                        furnitureVisual(item.category).image ? "pet-placed-item--image" : ""
                      } pet-placed-item--${item.category.toLowerCase()} ${
                        furnitureBusy === item.id ? "is-busy" : ""
                      }`}
                      style={{ left: `${pos.x}%`, top: `${pos.y}%` }}
                      onPointerDown={(event) => handleFurniturePointerDown(item, event)}
                      onPointerMove={(event) =>
                        continueDrag({ type: "furniture", id: item.id }, event)
                      }
                      onPointerUp={endDrag}
                      onPointerCancel={endDrag}
                      onDoubleClick={(event) => event.preventDefault()}
                      aria-label={`${item.name} 이동`}
                      title={`${item.name} 드래그 이동, 더블클릭 회수`}
                    >
                      <FurnitureArt
                        category={item.category}
                        name={item.name}
                        className="pet-placed-image"
                        emojiClassName="pet-placed-emoji"
                      />
                    </button>
                  );
                })}

                {selectedPet ? (
                  <div
                    className={`pet-current ${
                      dragTarget?.type === "pet" ? "is-dragging" : ""
                    }`}
                    style={{
                      "--pet-accent": selectedPet.color,
                      left: `${petPosition.x}%`,
                      top: `${petPosition.y}%`,
                    }}
                    ref={petRef}
                    role="button"
                    tabIndex={0}
                    onPointerDown={(event) => startDrag({ type: "pet", id: selectedPet.id }, event)}
                    onPointerMove={(event) =>
                      continueDrag({ type: "pet", id: selectedPet.id }, event)
                    }
                    onPointerUp={endDrag}
                    onPointerCancel={endDrag}
                    onContextMenu={openPetMenu}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") openPetMenu(event);
                    }}
                    aria-haspopup="menu"
                    aria-expanded={petMenu !== null}
                    aria-label={`${selectedPet.name} 이동, 상호작용 메뉴 열기`}
                    title={`${selectedPet.name} 드래그 이동, 우클릭 상호작용`}
                  >
                    {reaction && (
                      <PetReaction
                        key={reaction.id}
                        reaction={reaction}
                        petPosition={petPosition}
                      />
                    )}
                    <span className="pet-current-shadow" aria-hidden />
                    <span
                      ref={petIconRef}
                      className="pet-current-icon"
                      aria-label={selectedPet.name}
                    >
                      <PetArt
                        appearanceKey={selectedPet.appearanceKey}
                        stage={selectedPet.stage}
                        name={selectedPet.name}
                        className="pet-current-image"
                        emojiClassName="pet-current-emoji"
                      />
                    </span>
                  </div>
                ) : (
                  !petLoading && (
                    <div className="pet-room-empty">
                      <strong>아직 키우는 펫이 없어요</strong>
                      <p>상점에서 새 친구를 데려올 수 있어요.</p>
                      <button
                        type="button"
                        className="home-btn home-btn-primary"
                        onClick={() => navigate("/shop")}
                      >
                        상점 가기
                      </button>
                    </div>
                  )
                )}
              </div>

              {/* 펫 반응 대사를 스크린리더에 알리는 상시 영역 — 보이는 말풍선은 aria-hidden */}
              <p className="pet-reaction-status" role="status">
                {reaction && selectedPet
                  ? `${selectedPet.name}: ${reaction.line}${reaction.charmUp ? " 매력이 1 올랐어요." : ""}`
                  : ""}
              </p>

              <RoomTips />
            </section>
          </div>

          {/* 오른쪽: 펫 상태 · 대화방 · 방 색상 · 보유 가구 · 아이템 · 내역을 한 카드에서 탭으로 전환 */}
          <aside className="pet-myroom-side">
            <section className="home-card pet-panel pet-tab-card">
              <div className="pet-tab-bar" role="tablist" aria-label="마이룸 메뉴">
                {PANEL_TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    role="tab"
                    aria-selected={activeTab === tab.id}
                    className={`pet-tab-btn ${activeTab === tab.id ? "is-active" : ""}`}
                    onClick={() => setActiveTab(tab.id)}
                  >
                    {tab.label}
                  </button>
                ))}
                <button
                  type="button"
                  role="tab"
                  aria-selected={activeTab === HISTORY_TAB}
                  className={`pet-tab-btn pet-tab-btn--history ${activeTab === HISTORY_TAB ? "is-active" : ""}`}
                  onClick={() => setActiveTab(HISTORY_TAB)}
                >
                  내역 보기
                </button>
              </div>

              {activeTab === "pet" && (
                <div className="pet-tab-panel">
                  <div className="pet-panel-head">
                    <h2 className="home-card-title home-card-title--sm">펫 상태</h2>
                    <span>{pet ? "한 마리만 키우는 중" : petLoading ? "불러오는 중…" : "펫 없음"}</span>
                  </div>
                  {petError && <p className="pet-inline-error">{petError}</p>}
                  {pet && selectedPet && (
                    <>
                      <div className="pet-profile-card" style={{ "--pet-accent": selectedPet.color }}>
                        <span className="pet-profile-icon">
                          <PetArt
                            appearanceKey={selectedPet.appearanceKey}
                            stage={selectedPet.stage}
                            name={selectedPet.name}
                            className="pet-profile-image"
                            emojiClassName="pet-profile-emoji"
                          />
                        </span>
                        <div>
                          <strong>{selectedPet.name}</strong>
                          <small>{selectedPet.type}</small>
                          <p>{formatDate(pet.hatchedAt)} 부화 · Lv. {pet.level}</p>
                        </div>
                      </div>

                      {/* 레벨 진행도 — 레벨·진행 경험치는 서버(PetLevel) 값. 5레벨 2차, 15레벨 3차, 30레벨 졸업 */}
                      <div className="pet-evolve">
                        <div className="pet-evolve-head">
                          <span>Lv. {pet.level} / {pet.maxLevel}</span>
                          <strong>
                            {graduated
                              ? "졸업 가능"
                              : `다음 레벨까지 ${pet.levelExp} / ${pet.levelExpNeeded}`}
                          </strong>
                        </div>
                        <div className="pet-status-track">
                          <div
                            className="pet-status-fill"
                            style={{ width: `${levelProgressPct(pet)}%`, background: "var(--home-green)" }}
                          />
                        </div>
                        <p className="pet-evolve-help">
                          {milestone
                            ? `Lv. ${milestone.level}에 ${milestone.label}. 하루 소비 판정과 출석 보상으로 경험치가 쌓여요.`
                            : "30레벨을 달성했어요! 분양해서 졸업시키면 코인으로 바꿀 수 있어요. 분양가 = EXP."}
                        </p>
                        {graduated && (
                          <button
                            type="button"
                            className="home-btn home-btn-primary pet-release-btn"
                            disabled={releasing}
                            onClick={handleRelease}
                          >
                            {releasing ? "분양 중…" : `분양하기 (${coin(pet.exp)}~)`}
                          </button>
                        )}
                      </div>

                      {/* 누적 스탯 — 합리적인 지출을 기록하면 자란다 */}
                      <ul className="pet-status-list" aria-label="누적 스탯">
                        {STAT_META.map((meta) => {
                          const value = pet?.[meta.key] ?? 0;
                          const width = Math.min(Math.max(value, 0), 100);
                          return (
                            <li key={meta.key}>
                              <div className="pet-status-row">
                                <span>{meta.label}</span>
                                <strong>{value}</strong>
                              </div>
                              <div className="pet-status-track">
                                <div
                                  className="pet-status-fill"
                                  style={{ width: `${width}%`, background: meta.color }}
                                />
                              </div>
                            </li>
                          );
                        })}
                      </ul>
                    </>
                  )}
                  {!pet && !petLoading && !petError && (
                    <p className="pet-empty">
                      키우는 펫이 없어요. 상점에서 알을 개봉해 새 친구를 만나 보세요.
                    </p>
                  )}
                </div>
              )}
              {activeTab === "chat" &&
                (pet ? (
                  <PetChatPanel key={pet.id} pet={pet} />
                ) : (
                  <div className="pet-tab-panel">
                    <p className="pet-empty">키우는 펫이 있어야 대화할 수 있어요.</p>
                  </div>
                ))}
              {activeTab === "background" && (
                <div className="pet-tab-panel">
                  <div className="pet-panel-head">
                    <h2 className="home-card-title home-card-title--sm">방 색상</h2>
                    <span>이 브라우저에만 저장</span>
                  </div>
                  <div className="pet-background-list">
                    {BACKGROUNDS.map((background) => (
                      <button
                        key={background.id}
                        type="button"
                        className={`pet-background-card ${background.className} ${
                          selectedBackgroundId === background.id ? "is-selected" : ""
                        }`}
                        style={
                          background.image
                            ? { backgroundImage: `url(${background.image})` }
                            : undefined
                        }
                        onClick={() => chooseBackground(background.id)}
                      >
                        <span>{background.name}</span>
                        <strong>{background.slot}</strong>
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {activeTab === "furniture" && (
                <div className="pet-tab-panel">
                  <div className="pet-panel-head">
                    <h2 className="home-card-title home-card-title--sm">보유 가구</h2>
                    <span>
                      {furniture.filter((f) => f.placed).length}개 배치중 · {furniture.length}개 보유
                    </span>
                  </div>
                  <p className="pet-furniture-bonus-help">배치한 가구는 분양가 보너스와 함께, 해당 스탯 보상을 가구에 적힌 비율만큼 올려줘요.</p>
                  {!furnitureLoading && furniture.length === 0 ? (
                    <div className="pet-empty">
                      <p>아직 가구가 없어요. 상점에서 사서 배치하면 분양가가 올라가요.</p>
                      <button
                        type="button"
                        className="home-link-btn"
                        onClick={() => navigate("/shop?tab=furniture")}
                      >
                        가구 상점 가기 →
                      </button>
                    </div>
                  ) : (
                    <div className="pet-furniture-grid">
                      {furniture.map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          className={`pet-furniture-card ${item.placed ? "is-placed" : ""}`}
                          disabled={furnitureBusy !== null}
                          onClick={() => (item.placed ? removeFurniture(item) : placeFurniture(item))}
                          title={`${CATEGORY_LABEL[item.category] ?? ""} · ${
                            STAT_LABEL[item.statTarget] ?? ""
                          } · 분양가 +${item.releaseBonusPct}%`}
                        >
                          <span>
                            <FurnitureArt
                              category={item.category}
                              name={item.name}
                              className="pet-furniture-image"
                              emojiClassName="pet-furniture-emoji"
                            />
                          </span>
                          <strong>{item.name}</strong>
                          <small>
                            {furnitureBusy === item.id
                              ? "저장 중…"
                              : item.placed
                                ? "회수하기"
                                : "배치하기"}
                          </small>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
              {activeTab === "items" && (
                <div className="pet-tab-panel">
                  <div className="pet-panel-head"><h2 className="home-card-title home-card-title--sm">아이템</h2><span>{statItems.length}개 보유</span></div>
                  {statItems.length === 0 ? <p className="pet-empty">보유한 아이템이 없어요. 출석 탭에서 출석 보상을 받아 보세요.</p> : <div className="pet-item-list">{statItems.map((item) => <article key={item.id} className="pet-item-card"><div><strong>{item.name}</strong><span>{STAT_LABEL[item.statType]} +{item.statDelta}</span></div><button type="button" disabled={itemBusy || !pet} onClick={() => handleUseItem(item)}>사용하기</button></article>)}</div>}
                </div>
              )}
              {activeTab === HISTORY_TAB && <PetHistoryPanel petHistory={petHistory} />}
              {notice && (
                <p
                  className={`pet-notice ${notice.kind === "err" ? "pet-notice--err" : ""}`}
                  role={notice.kind === "err" ? "alert" : "status"}
                >
                  {notice.text}
                </p>
              )}
            </section>
          </aside>
        </div>

        {petMenu && selectedPet && (
          <PetActionMenu
            anchor={petMenu}
            petName={selectedPet.name}
            onSelect={handlePetAction}
            onClose={closePetMenu}
          />
        )}
      </main>
    </AppShell>
  );
}

export default PetPage;
