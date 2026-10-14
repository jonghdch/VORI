import { useEffect, useId, useRef } from "react";
import Icon from "../../components/Icon";

// 가계부 목록 탭의 시트 공용 틀 — 필터·검색이 같이 쓴다.
// 넓은 화면은 가운데 다이얼로그, 좁은 화면은 아래에서 올라오는 시트 (WalletScreen.css .lw-sheet*).
// 뒤 화면 스크롤 잠금 · Esc 닫기 · Tab 가두기 · 닫히면 여는 버튼으로 포커스 복귀 (PurchaseHistoryModal 과 같은 방식).
const FOCUSABLE =
  'button:not(:disabled), input:not(:disabled), select:not(:disabled), [href], [tabindex]:not([tabindex="-1"])';

function LedgerSheet({ title, onClose, children, footer, header, initialFocusRef, className = "" }) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const closeRef = useRef(null);

  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    (initialFocusRef?.current || closeRef.current)?.focus();
    const onEscape = (e) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onEscape);
    return () => {
      document.removeEventListener("keydown", onEscape);
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [onClose, initialFocusRef]);

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
        aria-label={title ? undefined : "내역 검색"}
        tabIndex={-1}
        onKeyDown={onKeyDown}
      >
        {header ?? (
          <header className="lw-sheet-head">
            <h2 id={titleId} className="lw-sheet-title">{title}</h2>
            <button ref={closeRef} type="button" className="hm-icon-btn" aria-label="닫기" onClick={onClose}>
              <Icon name="close" size={18} />
            </button>
          </header>
        )}
        <div className="lw-sheet-body">{children}</div>
        {footer && <div className="lw-sheet-foot">{footer}</div>}
      </div>
    </div>
  );
}

export default LedgerSheet;
