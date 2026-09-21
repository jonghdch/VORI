import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import {
  PetArt,
  STAGE_LABEL,
  TIER_LABEL,
  VARIANT_LABEL,
  nextStage,
} from "../../components/petVisual";
import { getActivePet, listPets, releasePet } from "../../api/pet";
import { listMyFurniture, placeFurniture as apiPlaceFurniture, unplaceFurniture } from "../../api/furniture";
import { listThemes } from "../../api/theme";
import {
  CATEGORY_LABEL,
  DEFAULT_POSITION,
  FurnitureArt,
  SURFACE_POSITION,
  STAT_LABEL,
  isSurface,
} from "../../components/furnitureVisual";
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
  { id: "pet", label: "현재 펫" },
  { id: "status", label: "펫 상태" },
  { id: "background", label: "방 색상" },
  { id: "furniture", label: "보유 가구" },
  { id: "history", label: "펫 이력" },
];

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

function PetPage({ user, onLogout }) {
  const roomStageRef = useRef(null);
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
    return () => {
      alive = false;
    };
  }, [loadPets, navigate]);

  const handleRelease = async () => {
    if (!pet) return;
    const ok = window.confirm(
      `${pet.speciesName}을(를) 분양할까요? 분양하면 더 이상 키울 수 없고, 스탯에 따라 코인을 받아요.`,
    );
    if (!ok) return;
    setReleasing(true);
    setNotice(null);
    try {
      const released = await releasePet(pet.id);
      await loadPets();
      setNotice({
        kind: "ok",
        text: `${released.speciesName}을(를) 분양하고 ${coin(released.releaseValue)}을 받았어요.`,
      });
    } catch (e) {
      setNotice({
        kind: "err",
        text:
          e.status === 400
            ? "성체가 된 펫만 분양할 수 있어요."
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
        name: pet.speciesName ?? "펫",
        type: [TIER_LABEL[pet.tier], STAGE_LABEL[pet.stage], VARIANT_LABEL[pet.variant]]
          .filter(Boolean)
          .join(" · "),
        appearanceKey: pet.appearanceKey,
        color: PET_ACCENT,
      }
    : null;
  const evolution = pet ? nextStage(pet.stage) : null;
  const selectedBackground =
    BACKGROUNDS.find((background) => background.id === selectedBackgroundId) ??
    BACKGROUNDS[0];

  // 방에 그리는 가구: 배치된 것 중 벽지·바닥(면)은 칩으로, 나머지는 드래그 가능한 물건으로.
  const placedFurniture = useMemo(
    () => furniture.filter((f) => f.placed && !isSurface(f.category)),
    [furniture],
  );
  const placedSurfaces = useMemo(
    () => furniture.filter((f) => f.placed && isSurface(f.category)),
    [furniture],
  );
  // 한 개라도 놓인 테마만 칩으로 보여준다. 0/3 까지 늘어놓으면 발동한 세트가 묻힌다.
  const setProgress = themes.filter((t) => t.placedCount > 0);
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
    setDragTarget(target);
    updateDragPosition(target, event);
  };

  const continueDrag = (target, event) => {
    if (
      !dragTarget ||
      dragTarget.type !== target.type ||
      dragTarget.id !== target.id
    ) {
      return;
    }
    updateDragPosition(target, event);
  };

  const endDrag = (event) => {
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture?.(event.pointerId);
    }
    // 가구 드래그가 끝나면 마지막 좌표를 서버에 저장한다(펫 위치는 로컬 전용)
    if (dragTarget?.type === "furniture") {
      const item = furniture.find((f) => f.id === dragTarget.id);
      const point = dragPositions[dragTarget.id];
      if (item && point) savePlacement(item, point);
    }
    setDragTarget(null);
  };

  return (
    <AppShell
      activeTop="raise"
      activeSide="raise"
      user={user}
      onLogout={onLogout}
    >
      <main className="home-main pet-main">
        <div className="pet-header">
          <div>
            <p className="pet-eyebrow">마이룸</p>
            <h1 className="pet-title">
              {selectedPet
                ? `${nickname}님이 키우는 ${selectedPet.name}의 방`
                : `${nickname}님의 방`}
            </h1>
          </div>
          {selectedPet && (
            <div className="pet-header-status">
              <span>{selectedPet.type}</span>
            </div>
          )}
        </div>

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
                <div>
                  <span className="pet-room-label">방 색상</span>
                  <h2>{selectedBackground.name}</h2>
                </div>
                <div className="pet-room-chips">
                  {setProgress.length > 0 && (
                    <ul className="pet-surface-chips pet-theme-chips" aria-label="테마 세트 현황">
                      {setProgress.map((t) => (
                        <li key={t.id} className={t.active ? "is-active" : ""}>
                          {t.name} {t.placedCount}/{t.requiredCount}
                          {t.active && ` 발동 +${t.setBonusPct}%`}
                        </li>
                      ))}
                    </ul>
                  )}
                  {placedSurfaces.length > 0 && (
                    <ul className="pet-surface-chips" aria-label="적용된 벽지·바닥">
                      {placedSurfaces.map((f) => (
                        <li key={f.id}>
                          {CATEGORY_LABEL[f.category]} · {f.name}
                        </li>
                      ))}
                    </ul>
                  )}
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
                      } ${item.category === "BED" ? "pet-placed-item--image" : ""} ${
                        furnitureBusy === item.id ? "is-busy" : ""
                      }`}
                      style={{ left: `${pos.x}%`, top: `${pos.y}%` }}
                      onPointerDown={(event) =>
                        startDrag({ type: "furniture", id: item.id }, event)
                      }
                      onPointerMove={(event) =>
                        continueDrag({ type: "furniture", id: item.id }, event)
                      }
                      onPointerUp={endDrag}
                      onPointerCancel={endDrag}
                      onDoubleClick={() => removeFurniture(item)}
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
                    role="button"
                    tabIndex={0}
                    onPointerDown={(event) => startDrag({ type: "pet", id: selectedPet.id }, event)}
                    onPointerMove={(event) =>
                      continueDrag({ type: "pet", id: selectedPet.id }, event)
                    }
                    onPointerUp={endDrag}
                    onPointerCancel={endDrag}
                    aria-label={`${selectedPet.name} 이동`}
                    title={`${selectedPet.name} 드래그 이동`}
                  >
                    <span className="pet-current-shadow" aria-hidden />
                    <span className="pet-current-icon" aria-label={selectedPet.name}>
                      <PetArt
                        appearanceKey={selectedPet.appearanceKey}
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
                      <p>상점에서 알을 데려와 개봉하면 새 친구가 태어나요.</p>
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

              <p className="pet-room-help">
                펫과 가구를 드래그해서 원하는 위치에 배치해요. 가구는 더블클릭하면 인벤토리로 회수돼요.
                배치한 가구만 분양가 보너스에 반영돼요.
              </p>
            </section>
          </div>

          {/* 오른쪽: 현재 펫 · 펫 상태 · 방 색상 · 보유 가구 · 펫 이력을 한 카드에서 탭으로 전환 */}
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
              </div>

              {activeTab === "pet" && (
                <div className="pet-tab-panel">
                  <div className="pet-panel-head">
                    <h2 className="home-card-title home-card-title--sm">현재 펫</h2>
                    <span>{pet ? "한 마리만 키우는 중" : petLoading ? "불러오는 중…" : "펫 없음"}</span>
                  </div>
                  {petError && <p className="pet-inline-error">{petError}</p>}
                  {pet && selectedPet && (
                    <>
                      <div className="pet-profile-card" style={{ "--pet-accent": selectedPet.color }}>
                        <span className="pet-profile-icon">
                          <PetArt
                            appearanceKey={selectedPet.appearanceKey}
                            name={selectedPet.name}
                            className="pet-profile-image"
                            emojiClassName="pet-profile-emoji"
                          />
                        </span>
                        <div>
                          <strong>{selectedPet.name}</strong>
                          <small>{selectedPet.type}</small>
                          <p>{formatDate(pet.hatchedAt)} 부화 · 스탯 합 {pet.statTotal}</p>
                        </div>
                      </div>

                      {/* 진화 진행도 — 임계값은 백엔드와 동일(200/300) */}
                      <div className="pet-evolve">
                        <div className="pet-evolve-head">
                          <span>
                            {evolution
                              ? `${STAGE_LABEL[evolution.stage]}까지`
                              : "최종 단계"}
                          </span>
                          <strong>
                            {evolution
                              ? `${Math.min(pet.statTotal, evolution.threshold)} / ${evolution.threshold}`
                              : "성체 완료"}
                          </strong>
                        </div>
                        <div className="pet-status-track">
                          <div
                            className="pet-status-fill"
                            style={{
                              width: evolution
                                ? `${Math.min(100, (pet.statTotal / evolution.threshold) * 100)}%`
                                : "100%",
                              background: "var(--home-green)",
                            }}
                          />
                        </div>
                        <p className="pet-evolve-help">
                          {evolution
                            ? "합리적인 지출로 절약하면 스탯이 올라 다음 단계로 자라요."
                            : "다 자란 펫은 분양해서 코인으로 바꿀 수 있어요. 분양가 = 스탯 합 × 10."}
                        </p>
                        {pet.stage === "ADULT" && (
                          <button
                            type="button"
                            className="home-btn home-btn-primary pet-release-btn"
                            disabled={releasing}
                            onClick={handleRelease}
                          >
                            {releasing ? "분양 중…" : `분양하기 (${coin(pet.statTotal * 10)}~)`}
                          </button>
                        )}
                      </div>
                    </>
                  )}
                  {!pet && !petLoading && !petError && (
                    <p className="pet-empty">
                      키우는 펫이 없어요. 상점에서 알을 개봉해 새 친구를 만나 보세요.
                    </p>
                  )}
                </div>
              )}
              {activeTab === "status" && (
                <div className="pet-tab-panel">
                  <div className="pet-panel-head">
                    <h2 className="home-card-title home-card-title--sm">펫 상태</h2>
                    <span>누적 스탯</span>
                  </div>
                  <div className="pet-status-summary">
                    <div>
                      <strong>{selectedPet?.name ?? "펫"}</strong>
                      <p>합리적인 지출을 기록하면 스탯이 자라요.</p>
                    </div>
                    {selectedPet && (
                      <PetArt
                        appearanceKey={selectedPet.appearanceKey}
                        name={selectedPet.name}
                        className="pet-status-image"
                        emojiClassName="pet-status-emoji"
                      />
                    )}
                  </div>
                  <ul className="pet-status-list">
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
                </div>
              )}
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
                  {!furnitureLoading && furniture.length === 0 ? (
                    <div className="pet-empty">
                      <p>아직 가구가 없어요. 상점에서 사서 배치하면 분양가가 올라가요.</p>
                      <button
                        type="button"
                        className="home-link-btn"
                        onClick={() => navigate("/shop")}
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
              {activeTab === "history" && (
                <div className="pet-tab-panel">
                  <div className="pet-panel-head">
                    <h2 className="home-card-title home-card-title--sm">펫 이력</h2>
                    <span>{petHistory.length}마리</span>
                  </div>
                  {petHistory.length === 0 ? (
                    <p className="pet-empty">아직 함께한 펫이 없어요.</p>
                  ) : (
                    <ul className="pet-history-list">
                      {petHistory.map((p) => (
                        <li key={p.id} className={`pet-history-item ${p.releasedAt ? "is-released" : ""}`}>
                          <span className="pet-history-art">
                            <PetArt
                              appearanceKey={p.appearanceKey}
                              name={p.speciesName}
                              className="pet-history-image"
                              emojiClassName="pet-history-emoji"
                            />
                          </span>
                          <div className="pet-history-info">
                            <strong>{p.speciesName}</strong>
                            <small>
                              {[TIER_LABEL[p.tier], STAGE_LABEL[p.stage], VARIANT_LABEL[p.variant]]
                                .filter(Boolean)
                                .join(" · ")}
                            </small>
                          </div>
                          <span className="pet-history-state">
                            {p.releasedAt
                              ? `${formatDate(p.releasedAt)} 분양 · ${coin(p.releaseValue)}`
                              : "키우는 중"}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
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
      </main>
    </AppShell>
  );
}

export default PetPage;
