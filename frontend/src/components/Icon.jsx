/**
 * 공용 선 아이콘 — 이모지 대신 쓴다. 이모지는 기기마다 모양·색이 달라 화면 톤이 깨졌다.
 * 24×24 격자, 1.8px 선, 끝 둥글게. 색은 currentColor 를 따라가고 크기는 size(기본 1em).
 * 글자와 같은 뜻을 전하는 장식이면 aria-hidden(기본). 아이콘만 있는 버튼은 버튼에 aria-label 을 단다.
 */
const PATHS = {
  home: (
    <>
      <path d="M3.5 10.5 12 3.5l8.5 7" />
      <path d="M5.5 9v10.5h13V9" />
      <path d="M10 19.5v-5.5h4v5.5" />
    </>
  ),
  ledger: (
    <>
      <rect x="4.5" y="3.5" width="15" height="17" rx="2" />
      <path d="M8.5 8h7M8.5 12h7M8.5 16h4" />
    </>
  ),
  store: (
    <>
      <path d="M4 9.5 5.5 4h13L20 9.5" />
      <path d="M4 9.5a2.7 2.7 0 0 0 5.3 0 2.7 2.7 0 0 0 5.4 0 2.7 2.7 0 0 0 5.3 0" />
      <path d="M5.5 11.5v8h13v-8" />
      <path d="M10 19.5v-4.5h4v4.5" />
    </>
  ),
  room: (
    <>
      <path d="M5 11V8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v3" />
      <path d="M3.5 13a1.5 1.5 0 0 1 3 0v2h11v-2a1.5 1.5 0 0 1 3 0v4.5h-17Z" />
      <path d="M6 17.5V20M18 17.5V20" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  chevronRight: <path d="m9.5 6 6 6-6 6" />,
  chevronLeft: <path d="m14.5 6-6 6 6 6" />,
  chevronDown: <path d="m6 9.5 6 6 6-6" />,
  pencil: (
    <>
      <path d="M15.5 5.5 18.5 8.5 9 18H6v-3Z" />
      <path d="m13.5 7.5 3 3" />
    </>
  ),
  close: <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />,
  egg: <path d="M12 3.5c-3.6 0-6.5 5.4-6.5 9.6A6.5 6.5 0 0 0 12 20.5a6.5 6.5 0 0 0 6.5-7.4C18.5 8.9 15.6 3.5 12 3.5Z" />,
  sofa: (
    <>
      <path d="M5.5 10V8a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v2" />
      <path d="M3.5 12a1.5 1.5 0 0 1 3 0v1.5h11V12a1.5 1.5 0 0 1 3 0v5h-17Z" />
      <path d="M5.5 17v2M18.5 17v2" />
    </>
  ),
  lock: (
    <>
      <rect x="5.5" y="10.5" width="13" height="9.5" rx="2" />
      <path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" />
    </>
  ),
  camera: (
    <>
      <path d="M4 8.5A1.5 1.5 0 0 1 5.5 7h2.3L9.5 5h5l1.7 2h2.3A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5Z" />
      <circle cx="12" cy="12.8" r="3.3" />
    </>
  ),
  trash: (
    <>
      <path d="M4.5 7h15M9.5 7V4.5h5V7" />
      <path d="M6.5 7l1 13h9l1-13M10.5 11v5.5M13.5 11v5.5" />
    </>
  ),
  piggy: (
    <>
      <path d="M5 12.5a6.5 5.5 0 0 1 11.6-3.4l2.9-.6-.8 3.1a5 5 0 0 1 .3 1.4v1.5l-2 .8-1 2.7H14v-1.5h-3V18H9l-1-2.7a5.3 5.3 0 0 1-3-2.8Z" />
      <path d="M10 7.5a2.5 2.5 0 0 1 3.5-1" />
      <path d="M15.5 11.3v.2" />
    </>
  ),
  wallet: (
    <>
      <path d="M4.5 7.5A2 2 0 0 1 6.5 5.5h11v3" />
      <rect x="4.5" y="8.5" width="15" height="11" rx="2" />
      <path d="M15.5 14h.2" />
    </>
  ),
  arrowRight: <path d="M5 12h14M13 6l6 6-6 6" />,
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  checkCircle: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="m8.3 12.3 2.6 2.6 4.8-5.2" />
    </>
  ),
  minusCircle: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M8 12h8" />
    </>
  ),
  alertCircle: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5v5.5" />
      <path d="M12 16.3v.2" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5" />
      <path d="M12 7.8v.2" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8.5" r="3.5" />
      <path d="M5 19.5a7 7 0 0 1 14 0" />
    </>
  ),
  paw: (
    <>
      <ellipse cx="7" cy="10" rx="1.8" ry="2.3" />
      <ellipse cx="10.3" cy="6.3" rx="1.8" ry="2.3" />
      <ellipse cx="13.7" cy="6.3" rx="1.8" ry="2.3" />
      <ellipse cx="17" cy="10" rx="1.8" ry="2.3" />
      <path d="M12 12c-2.6 0-5 2.8-5 5 0 1.6 1.4 2.3 2.8 2 .8-.2 1.4-.5 2.2-.5s1.4.3 2.2.5c1.4.3 2.8-.4 2.8-2 0-2.2-2.4-5-5-5Z" />
    </>
  ),
  calendar: (
    <>
      <rect x="4" y="5" width="16" height="15" rx="2" />
      <path d="M4 9.5h16M8.5 3v4M15.5 3v4" />
    </>
  ),
  report: (
    <>
      <path d="M5 19.5V11M10 19.5V5M15 19.5v-6M20 19.5V8.5" />
    </>
  ),
  book: (
    <>
      <path d="M4.5 5.5A1.5 1.5 0 0 1 6 4h13.5v14H6a1.5 1.5 0 0 0-1.5 1.5Z" />
      <path d="M4.5 19.5A1.5 1.5 0 0 0 6 21h13.5v-3" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3.5v2.2M12 18.3v2.2M3.5 12h2.2M18.3 12h2.2M6 6l1.6 1.6M16.4 16.4 18 18M6 18l1.6-1.6M16.4 7.6 18 6" />
    </>
  ),
  tool: (
    <path d="M14.5 5.5a4 4 0 0 0-5 5L4 16v4h4l5.5-5.5a4 4 0 0 0 5-5L16 12l-4-4Z" />
  ),
  // 지출 카테고리
  meal: (
    <>
      <path d="M7 3.5v7a2 2 0 0 0 2 2v8M11 3.5v7a2 2 0 0 1-2 2M9 3.5v5" />
      <path d="M16.5 20.5v-17c-1.8 1-3 3.3-3 6.5 0 2 1.2 3 3 3" />
    </>
  ),
  cafe: (
    <>
      <path d="M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5Z" />
      <path d="M16 10.5h1.5a2.5 2.5 0 0 1 0 5H16" />
      <path d="M8.5 3.5v2.5M12 3.5v2.5" />
    </>
  ),
  bus: (
    <>
      <rect x="5" y="3.5" width="14" height="14" rx="2.5" />
      <path d="M5 11h14M8 17.5v2.5M16 17.5v2.5" />
      <path d="M8.5 14.3v.2M15.5 14.3v.2" />
    </>
  ),
  bag: (
    <>
      <path d="M5.5 8h13l-1 12h-11Z" />
      <path d="M9 10V7a3 3 0 0 1 6 0v3" />
    </>
  ),
  ticket: (
    <>
      <path d="M4 7.5h16v3a1.8 1.8 0 0 0 0 3.5v3H4v-3a1.8 1.8 0 0 0 0-3.5Z" />
      <path d="M14 7.5v10" strokeDasharray="1.5 2" />
    </>
  ),
  receipt: (
    <>
      <path d="M6 3.5h12v17l-2-1.3-2 1.3-2-1.3-2 1.3-2-1.3-2 1.3Z" />
      <path d="M9 8.5h6M9 12h6M9 15.5h3" />
    </>
  ),
  gift: (
    <>
      <rect x="4" y="9" width="16" height="4" rx="1" />
      <path d="M5.5 13v7h13v-7M12 9v11" />
      <path d="M12 9c-1-3-5-4-5-1.5C7 9 12 9 12 9Zm0 0c1-3 5-4 5-1.5C17 9 12 9 12 9Z" />
    </>
  ),
};

function Icon({ name, size = "1em", className = "", strokeWidth = 1.8, title }) {
  const shape = PATHS[name] ?? PATHS.receipt;
  return (
    <svg
      className={`vori-icon ${className}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : "true"}
      role={title ? "img" : undefined}
      aria-label={title}
      focusable="false"
    >
      {shape}
    </svg>
  );
}

export default Icon;
