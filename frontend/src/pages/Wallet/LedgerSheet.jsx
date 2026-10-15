import { useEffect, useId, useRef } from "react";
import Icon from "../../components/Icon";

// 가계부 목록 탭의 시트 공용 틀 — 필터·검색이 같이 쓴다.
// 넓은 화면은 가운데 다이얼로그, 좁은 화면은 아래에서 올라오는 시트 (WalletScreen.css .lw-sheet*).
// 뒤 화면 스크롤 잠금 · Esc 닫기 · Tab 가두기 · 닫히면 여는 버튼으로 포커스 복귀 (PurchaseHistoryModal 과 같은 방식).
const FOCUSABLE =
  'button:not(:disabled), input:not(:disabled), select:not(:disabled), [href], [tabindex]:not([tabindex="-1"])';

// showClose={false}: × 없이 바깥(어두운 배경)을 누르거나 Esc 로만 닫는다 — 처음 포커스는 시트 자체로.
// ariaLabel: 제목(title) 없이 쓸 때 보조기기가 읽을 시트 이름.
function LedgerSheet({
  title,
  ariaLabel,
  onClose,
  children,
  footer,
  header,
  initialFocusRef,
  className = "",
  showClose = true,
}) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  // 부모가 렌더마다 새 onClose 를 넘겨도 아래 effect 가 다시 돌지 않게 ref 로 든다.
  // effect 가 다시 돌면 cleanup 이 포커스를 뒤 화면으로 돌렸다가 시트로 다시 가져와,
  // 키보드·스크린리더 사용자의 위치가 시트 맨 앞으로 튄다(가계부의 60초 타이머 등으로 부모가 다시 그려질 때).
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    (initialFocusRef?.current || closeRef.current || dialogRef.current)?.focus();
    const onEscape = (e) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    document.addEventListener("keydown", onEscape);
    return () => {
      document.removeEventListener("keydown", onEscape);
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
    // 시트가 열릴 때 한 번만 — initialFocusRef 는 부모의 useRef 라 바뀌지 않는다
  }, [initialFocusRef]);

  const onKeyDown = (e) => {
    if (e.key !== "Tab") return;
    const focusable = dialogRef.current?.querySelectorAll(FOCUSABLE);
    if (!focusable || focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  };

  return (
    <div
      className="lw-sheet-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className={`lw-sheet ${className}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-label={title ? undefined : ariaLabel}
        tabIndex={-1}
        onKeyDown={onKeyDown}
      >
        <span className="lw-sheet-grip" aria-hidden />
        {header ?? (
          <header className="lw-sheet-head">
            <h2 id={titleId} className="lw-sheet-title">{title}</h2>
            {showClose && (
              <button ref={closeRef} type="button" className="hm-icon-btn" aria-label="닫기" onClick={onClose}>
                <Icon name="close" size={18} />
              </button>
            )}
          </header>
        )}
        <div className="lw-sheet-body">{children}</div>
        {footer && <div className="lw-sheet-foot">{footer}</div>}
      </div>
    </div>
  );
}

export default LedgerSheet;
