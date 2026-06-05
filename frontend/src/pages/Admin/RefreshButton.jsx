// 어드민 페이지 타이틀 줄 우측의 아이콘 새로고침 버튼. onClick 으로 데이터 reload.
function RefreshButton({ onClick, disabled }) {
  return (
    <button
      type="button"
      className="admin-refresh-btn"
      onClick={onClick}
      disabled={disabled}
      aria-label="새로고침"
      title="새로고침"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        width="18"
        height="18"
        aria-hidden
      >
        <path d="M21 12a9 9 0 1 1-2.64-6.36" />
        <path d="M21 4v5h-5" />
      </svg>
    </button>
  );
}

export default RefreshButton;
