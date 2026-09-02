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
import roomDefaultImage from "../../assets/backgrounds/room-default.png";
import roomGreenImage from "../../assets/backgrounds/room-green.png";
import roomPinkImage from "../../assets/backgrounds/room-pink.png";
import roomYellowImage from "../../assets/backgrounds/room-yellow.png";
import bedImage from "../../assets/furniture/bed.png";
import "../Home/HomeDashboard.css";
import "./PetPage.css";

// 펫은 GET /api/pets/active 로 받는다. 배경·가구는 아직 백엔드 API 가 없어 목업 유지.
const PET_ACCENT = "#f2c27b";

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
    id: "yellow",
    slot: "2번 슬롯",
    name: "노란 방",
    owned: true,
    className: "pet-room-bg--image",
    image: roomYellowImage,
  },
  {
    id: "pink",
    slot: "3번 슬롯",
    name: "분홍 방",
    owned: true,
    className: "pet-room-bg--image",
    image: roomPinkImage,
  },
  {
    id: "green",
    slot: "4번 슬롯",
    name: "연두 방",
    owned: true,
    className: "pet-room-bg--image",
    image: roomGreenImage,
  },
];

const FURNITURE = [
  { id: "bed", name: "포근한 침대", icon: "🛏️", image: bedImage, owned: true },
  { id: "sofa", name: "초록 소파", icon: "🛋️", owned: true },
  { id: "plant", name: "화분", icon: "🪴", owned: true },
  { id: "lamp", name: "스탠드", icon: "💡", owned: true },
  { id: "rug", name: "체크 러그", icon: "🧺", owned: true },
  { id: "books", name: "책장", icon: "📚", owned: false },
];

const INITIAL_PLACED_FURNITURE = ["bed", "plant", "rug"];

const INITIAL_PET_POSITION = { x: 50, y: 62 };

const INITIAL_FURNITURE_POSITIONS = {
  bed: { x: 15, y: 74 },
  sofa: { x: 77, y: 73 },
  plant: { x: 83, y: 64 },
  lamp: { x: 18, y: 32 },
  rug: { x: 36, y: 82 },
  books: { x: 62, y: 38 },
};

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

  const [selectedBackgroundId, setSelectedBackgroundId] = useState("default");
  const [placedFurnitureIds, setPlacedFurnitureIds] = useState(INITIAL_PLACED_FURNITURE);
  const [petPosition, setPetPosition] = useState(INITIAL_PET_POSITION);
  const [furniturePositions, setFurniturePositions] = useState(
    INITIAL_FURNITURE_POSITIONS,
  );
  const [dragTarget, setDragTarget] = useState(null);

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

  const placedFurniture = useMemo(
    () =>
      placedFurnitureIds
        .map((id) => FURNITURE.find((item) => item.id === id))
        .filter(Boolean),
    [placedFurnitureIds],
  );

  const placeFurniture = (item) => {
    if (!item.owned || placedFurnitureIds.includes(item.id)) return;
    setPlacedFurnitureIds((ids) => [...ids, item.id]);
    setFurniturePositions((positions) => ({
      ...positions,
      [item.id]: positions[item.id] ?? INITIAL_FURNITURE_POSITIONS[item.id] ?? { x: 50, y: 72 },
    }));
  };

  const removeFurniture = (itemId) => {
    setPlacedFurnitureIds((ids) => ids.filter((id) => id !== itemId));
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

    setFurniturePositions((positions) => ({
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
              <span className="pet-room-label">현재 배경</span>
              <h2>{selectedBackground.name}</h2>
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

            {placedFurniture.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`pet-placed-item ${
                  dragTarget?.type === "furniture" && dragTarget.id === item.id
                    ? "is-dragging"
                    : ""
                } ${item.image ? "pet-placed-item--image" : ""}`}
                style={{
                  left: `${furniturePositions[item.id]?.x ?? 50}%`,
                  top: `${furniturePositions[item.id]?.y ?? 72}%`,
                }}
                onPointerDown={(event) =>
                  startDrag({ type: "furniture", id: item.id }, event)
                }
                onPointerMove={(event) =>
                  continueDrag({ type: "furniture", id: item.id }, event)
                }
                onPointerUp={endDrag}
                onPointerCancel={endDrag}
                onDoubleClick={() => removeFurniture(item.id)}
                aria-label={`${item.name} 이동`}
                title={`${item.name} 드래그 이동, 더블클릭 삭제`}
              >
                {item.image ? (
                  <img
                    src={item.image}
                    alt={item.name}
                    className="pet-placed-image"
                  />
                ) : (
                  <span>{item.icon}</span>
                )}
              </button>
            ))}

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
            펫과 가구를 드래그해서 원하는 위치에 배치해요. 가구는 더블클릭하면 방에서 삭제돼요.
          </p>
        </section>

        <div className="pet-content-grid">
          <section className="home-card pet-panel">
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
            {notice && (
              <p
                className={`pet-notice ${notice.kind === "err" ? "pet-notice--err" : ""}`}
                role={notice.kind === "err" ? "alert" : "status"}
              >
                {notice.text}
              </p>
            )}
          </section>

          <section className="home-card pet-panel">
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
          </section>

          <section className="home-card pet-panel">
            <div className="pet-panel-head">
              <h2 className="home-card-title home-card-title--sm">배경 변경</h2>
              <span>상점 구매 항목</span>
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
                  disabled={!background.owned}
                  onClick={() => setSelectedBackgroundId(background.id)}
                >
                  <span>{background.name}</span>
                  <strong>
                    {background.owned
                      ? `${background.slot} · 보유중`
                      : `${background.slot} · 상점 구매 필요`}
                  </strong>
                </button>
              ))}
            </div>
          </section>

          <section className="home-card pet-panel">
            <div className="pet-panel-head">
              <h2 className="home-card-title home-card-title--sm">보유 가구</h2>
              <span>{placedFurniture.length}개 배치중</span>
            </div>
            <div className="pet-furniture-grid">
              {FURNITURE.map((item) => {
                const isPlaced = placedFurnitureIds.includes(item.id);
                return (
                  <button
                    key={item.id}
                    type="button"
                    className={`pet-furniture-card ${isPlaced ? "is-placed" : ""}`}
                    disabled={!item.owned}
                    onClick={() =>
                      isPlaced ? removeFurniture(item.id) : placeFurniture(item)
                    }
                  >
                    <span>
                      {item.image ? (
                        <img
                          src={item.image}
                          alt={item.name}
                          className="pet-furniture-image"
                        />
                      ) : (
                        item.icon
                      )}
                    </span>
                    <strong>{item.name}</strong>
                    <small>
                      {!item.owned
                        ? "미보유"
                        : isPlaced
                          ? "삭제하기"
                          : "배치하기"}
                    </small>
                  </button>
                );
              })}
            </div>
          </section>

          <section className="home-card pet-panel">
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
          </section>
        </div>
      </main>
    </AppShell>
  );
}

export default PetPage;
