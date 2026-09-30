import { useEffect, useLayoutEffect, useRef, useState } from "react";

// 펫 우클릭 메뉴와 반응 연출. 매력 보너스(1%) 추첨은 서버 몫이라 여기서는 결과 표시만 한다.

/** 반응(말풍선·파티클)이 떠 있는 시간. PetPage.css 의 pet-reaction-bubble 애니메이션 길이와 같아야 한다. */
export const REACTION_MS = 2400;

// motion 은 펫 그림에 거는 Web Animations 키프레임. CSS 클래스로 걸면 같은 행동을 연달아
// 골랐을 때 애니메이션이 다시 시작되지 않아서 element.animate() 로 매번 새로 재생한다.
export const PET_ACTIONS = [
  {
    id: "pat",
    label: "쓰다듬기",
    particle: "♥︎",
    lines: ["기분 좋아요…", "조금만 더 쓰다듬어 주세요!", "헤헤, 간지러워요."],
    motion: {
      keyframes: [
        { transform: "scale(1, 1)" },
        { transform: "scale(1.07, 0.91)" },
        { transform: "scale(0.98, 1.03)" },
        { transform: "scale(1.05, 0.94)" },
        { transform: "scale(1, 1)" },
      ],
      duration: 900,
    },
  },
  {
    id: "praise",
    label: "칭찬하기",
    particle: "✦",
    lines: ["정말요? 더 열심히 자랄게요!", "칭찬받으니 힘이 나요!", "에헴, 제가 좀 하죠."],
    motion: {
      keyframes: [
        { transform: "translateY(0)" },
        { transform: "translateY(-20px)" },
        { transform: "translateY(0)" },
        { transform: "translateY(-9px)" },
        { transform: "translateY(0)" },
      ],
      duration: 720,
    },
  },
  {
    id: "greet",
    label: "인사하기",
    particle: "♪",
    lines: ["안녕하세요! 오늘도 반가워요.", "왔어요? 기다리고 있었어요.", "오늘 하루는 어땠어요?"],
    motion: {
      keyframes: [
        { transform: "rotate(0deg)" },
        { transform: "rotate(-9deg)" },
        { transform: "rotate(7deg)" },
        { transform: "rotate(-5deg)" },
        { transform: "rotate(0deg)" },
      ],
      duration: 820,
    },
  },
  {
    id: "play",
    label: "놀아주기",
    particle: "★",
    lines: ["우와, 신난다!", "한 번 더요, 한 번 더!", "같이 노니까 제일 좋아요."],
    motion: {
      keyframes: [
        { transform: "translateY(0) scaleX(1)" },
        { transform: "translateY(-24px) scaleX(-1)", offset: 0.4 },
        { transform: "translateY(0) scaleX(-1)", offset: 0.6 },
        { transform: "translateY(-12px) scaleX(1)", offset: 0.8 },
        { transform: "translateY(0) scaleX(1)" },
      ],
      duration: 980,
    },
  },
];

/** 행동의 대사 중 하나를 고른다. 직전 대사와 같은 건 피한다. */
export function pickLine(action, previousLine, random = Math.random) {
  const candidates = action.lines.filter((line) => line !== previousLine);
  const pool = candidates.length > 0 ? candidates : action.lines;
  return pool[Math.floor(random() * pool.length)];
}

const MENU_MARGIN = 8; // 메뉴와 화면 가장자리 사이 최소 간격(px)

/**
 * 펫 우클릭 메뉴. anchor 는 화면 좌표(clientX/clientY)이고, 화면 밖으로 나가면 안쪽으로 당긴다.
 * onClose(restoreFocus) — Esc·Tab 처럼 키보드로 닫을 때만 true 를 넘겨 펫으로 초점을 돌려준다.
 */
export function PetActionMenu({ anchor, petName, onSelect, onClose }) {
  const menuRef = useRef(null);
  const [position, setPosition] = useState(anchor);

  useLayoutEffect(() => {
    const menu = menuRef.current;
    if (!menu) return;
    const rect = menu.getBoundingClientRect();
    setPosition({
      x: Math.max(MENU_MARGIN, Math.min(anchor.x, window.innerWidth - rect.width - MENU_MARGIN)),
      y: Math.max(MENU_MARGIN, Math.min(anchor.y, window.innerHeight - rect.height - MENU_MARGIN)),
    });
    menu.querySelector('[role="menuitem"]')?.focus();
  }, [anchor]);

  useEffect(() => {
    const closeOnOutsidePress = (event) => {
      if (!menuRef.current?.contains(event.target)) onClose(false);
    };
    const close = () => onClose(false);
    document.addEventListener("pointerdown", closeOnOutsidePress, true);
    window.addEventListener("resize", close);
    window.addEventListener("scroll", close, true);
    window.addEventListener("blur", close);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsidePress, true);
      window.removeEventListener("resize", close);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("blur", close);
    };
  }, [onClose]);

  const handleKeyDown = (event) => {
    if (event.key === "Escape" || event.key === "Tab") {
      event.preventDefault();
      onClose(true);
      return;
    }
    const items = Array.from(menuRef.current?.querySelectorAll('[role="menuitem"]') ?? []);
    const current = items.indexOf(document.activeElement);
    const next = {
      ArrowDown: (current + 1) % items.length,
      ArrowUp: (current - 1 + items.length) % items.length,
      Home: 0,
      End: items.length - 1,
    }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    items[next]?.focus();
  };

  return (
    <div
      ref={menuRef}
      className="pet-action-menu"
      role="menu"
      aria-label={`${petName} 상호작용 메뉴`}
      style={{ left: position.x, top: position.y }}
      onKeyDown={handleKeyDown}
      onContextMenu={(event) => event.preventDefault()}
    >
      <p className="pet-action-menu-caption" aria-hidden>
        {petName}에게
      </p>
      {PET_ACTIONS.map((action) => (
        <button
          key={action.id}
          type="button"
          role="menuitem"
          className="pet-action-menu-item"
          onClick={() => onSelect(action)}
        >
          {action.label}
        </button>
      ))}
    </div>
  );
}

/**
 * 펫 위에 뜨는 말풍선과 파티클. 스크린리더용 안내는 PetPage 의 상시 status 영역이 맡으므로
 * 여기 있는 건 전부 aria-hidden 이다. 부르는 쪽에서 key={reaction.id} 를 줘야 반응마다 새로 재생된다.
 * @param {{ id:number, actionId:string, line:string, particle:string, charmUp?:boolean }} reaction
 *   charmUp 이면 "매력 +1" 표시를 함께 띄운다.
 * @param {{ x:number, y:number }} petPosition 방 안 펫 위치(%). 말풍선이 방 밖으로 잘리지 않게
 *   가로는 x 만큼 제 폭을 당기고(CSS --pet-x), 펫이 위쪽에 있으면 아래에 띄운다.
 */
export function PetReaction({ reaction, petPosition }) {
  return (
    <span
      className="pet-reaction"
      data-action={reaction.actionId}
      style={{ "--pet-x": petPosition.x }}
      aria-hidden
    >
      <span className={`pet-reaction-bubble ${petPosition.y < 34 ? "is-below" : ""}`}>
        {reaction.line}
      </span>
      <span className="pet-reaction-particles">
        <span>{reaction.particle}</span>
        <span>{reaction.particle}</span>
        <span>{reaction.particle}</span>
      </span>
      {reaction.charmUp && <span className="pet-reaction-gain">매력 +1</span>}
    </span>
  );
}
