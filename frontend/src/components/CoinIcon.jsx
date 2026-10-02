/**
 * 노란 코인 아이콘. 🪙 이모지는 기기마다 색이 달라(맥은 은색) 보상 화면에서 코인처럼 안 보였다.
 * 글자 크기를 따라가도록 1em 크기, currentColor 를 쓰지 않고 금색을 직접 칠한다.
 */
function CoinIcon({ className = "", size = "1em" }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      aria-hidden="true"
      focusable="false"
    >
      <circle cx="12" cy="12" r="11" fill="#e2a920" />
      <circle cx="12" cy="12" r="9.2" fill="#f7c948" />
      <circle cx="12" cy="12" r="6.6" fill="none" stroke="#e2a920" strokeWidth="1.6" />
      {/* 가운데 V — VORI 코인 */}
      <path
        d="M8.6 8.8 12 15.6l3.4-6.8"
        fill="none"
        stroke="#b8820f"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M8.2 6.6a7 7 0 0 1 3-1.6" fill="none" stroke="#fff3c4" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export default CoinIcon;
