import { useMemo, useRef, useState } from "react";
import AppShell from "../../components/AppShell";
import roomDefaultImage from "../../assets/backgrounds/room-default.png";
import roomGreenImage from "../../assets/backgrounds/room-green.png";
import roomPinkImage from "../../assets/backgrounds/room-pink.png";
import roomYellowImage from "../../assets/backgrounds/room-yellow.png";
import bedImage from "../../assets/furniture/bed.png";
import boriImage from "../../assets/pets/bori.png";
import "../Home/HomeDashboard.css";
import "./PetPage.css";

const CURRENT_PET = {
  id: "bori",
  name: "보리",
  type: "강아지",
  icon: "🐶",
  image: boriImage,
  color: "#f2c27b",
  description: "밝고 장난기 많은 현재 펫이에요.",
};

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

const PET_STATUS = [
  { key: "satiety", label: "에너지", value: 76, color: "var(--home-bar-green)" },
  { key: "mood", label: "매력", value: 88, color: "var(--home-bar-orange)" },
  { key: "clean", label: "지능", value: 62, color: "var(--home-bar-blue)" },
  { key: "energy", label: "지구력", value: 54, color: "var(--home-bar-red)" },
];

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function PetPage({ user, onLogout }) {
  const roomStageRef = useRef(null);
  const nickname = user?.nickname || "사용자";
  const [selectedBackgroundId, setSelectedBackgroundId] = useState("default");
  const [placedFurnitureIds, setPlacedFurnitureIds] = useState(INITIAL_PLACED_FURNITURE);
  const [petPosition, setPetPosition] = useState(INITIAL_PET_POSITION);
  const [furniturePositions, setFurniturePositions] = useState(
    INITIAL_FURNITURE_POSITIONS,
  );
  const [dragTarget, setDragTarget] = useState(null);

  const selectedPet = CURRENT_PET;
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
            <p className="pet-eyebrow">펫 키우기</p>
            <h1 className="pet-title">
              {nickname}님이 키우는 {selectedPet.name}의 방
            </h1>
          </div>
          <div className="pet-header-status">
            <span>{selectedPet.type}</span>
            <strong>Lv. 7</strong>
          </div>
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
                <img
                  src={selectedPet.image}
                  alt={selectedPet.name}
                  className="pet-current-image"
                />
              </span>
            </div>
          </div>

          <p className="pet-room-help">
            펫과 가구를 드래그해서 원하는 위치에 배치해요. 가구는 더블클릭하면 방에서 삭제돼요.
          </p>
        </section>

        <div className="pet-content-grid">
          <section className="home-card pet-panel">
            <div className="pet-panel-head">
              <h2 className="home-card-title home-card-title--sm">현재 펫</h2>
              <span>한 마리만 키우는 중</span>
            </div>
            <div className="pet-profile-card" style={{ "--pet-accent": selectedPet.color }}>
              <span className="pet-profile-icon">
                <img
                  src={selectedPet.image}
                  alt={selectedPet.name}
                  className="pet-profile-image"
                />
              </span>
              <div>
                <strong>{selectedPet.name}</strong>
                <small>{selectedPet.type} · Lv. 7</small>
                <p>{selectedPet.description}</p>
              </div>
            </div>
          </section>

          <section className="home-card pet-panel">
            <div className="pet-panel-head">
              <h2 className="home-card-title home-card-title--sm">펫 상태</h2>
              <span>오늘 상태</span>
            </div>
            <div className="pet-status-summary">
              <div>
                <strong>{selectedPet.name}</strong>
                <p>산책과 간식이 필요한 상태예요.</p>
              </div>
              <img
                src={selectedPet.image}
                alt={selectedPet.name}
                className="pet-status-image"
              />
            </div>
            <ul className="pet-status-list">
              {PET_STATUS.map((status) => (
                <li key={status.key}>
                  <div className="pet-status-row">
                    <span>{status.label}</span>
                    <strong>{status.value}%</strong>
                  </div>
                  <div className="pet-status-track">
                    <div
                      className="pet-status-fill"
                      style={{ width: `${status.value}%`, background: status.color }}
                    />
                  </div>
                </li>
              ))}
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
        </div>
      </main>
    </AppShell>
  );
}

export default PetPage;
