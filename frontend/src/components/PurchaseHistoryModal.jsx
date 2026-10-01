import { useCallback, useEffect, useId, useRef, useState } from "react";
import { listMyEggs } from "../api/pet";
import { listMyFurniture } from "../api/furniture";
import { FurnitureArt } from "./furnitureVisual";
import { eggImageFor } from "./eggVisual";
import "./PurchaseHistoryModal.css";

// 알은 등급 이름(EggResponse.gradeName)으로 이미지를 고르고, 가구는 종류(category)로 고른다.
const FOCUSABLE = 'button:not(:disabled), [href], [tabindex]:not([tabindex="-1"])';

const dateOf = (iso) => (iso ? iso.slice(0, 10).replaceAll("-", ". ") : "");
const coin = (n) => `${(n ?? 0).toLocaleString("ko-KR")} 코인`;

function PurchaseHistoryModal({ onClose }) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const closeRef = useRef(null);
  const [items, setItems] = useState(null); // null = 로딩 중
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setError(null);
    setItems(null);
    try {
      const [eggs, furniture] = await Promise.all([listMyEggs(false), listMyFurniture()]);
      const merged = [
        ...(eggs || []).map((e) => ({
          key: `egg-${e.id}`,
          kind: "egg",
          name: e.gradeName,
          image: eggImageFor(e.grade, e.gradeName),
          stage: e.opened ? "개봉함" : "미개봉",
          price: e.price,
          at: e.purchasedAt,
        })),
        ...(furniture || []).map((f) => ({
          key: `furniture-${f.id}`,
          kind: "furniture",
          name: f.name,
          category: f.category,
          stage: f.placed ? "배치 중" : "보관 중",
          price: f.price,
          at: f.acquiredAt,
        })),
      ].sort((a, b) => (b.at || "").localeCompare(a.at || ""));
      setItems(merged);
    } catch (e) {
      setError(e.message || "구매 내역을 불러오지 못했어요");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // 팝업이 떠 있는 동안 뒤 화면 스크롤 잠금, 닫기 버튼에 포커스, 닫힐 때 원래 포커스 복귀.
  // Esc 는 문서 레벨에서 듣는다 — 목록 글자를 클릭해 포커스가 밖으로 나가도 닫혀야 한다.
  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();
    const onEscape = (e) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onEscape);
    return () => {
      document.removeEventListener("keydown", onEscape);
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, [onClose]);

  // Tab 이 팝업 안에서만 돌게
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
      className="purchase-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className="purchase-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={onKeyDown}
      >
        <header className="purchase-head">
          <h2 id={titleId} className="purchase-title">
            구매 내역
          </h2>
          <button
            ref={closeRef}
            type="button"
            className="purchase-close"
            onClick={onClose}
            aria-label="닫기"
          >
            ×
          </button>
        </header>

        <div className="purchase-body">
          {error ? (
            <>
              <p className="purchase-state purchase-state--err" role="alert">
                {error}
              </p>
              <button type="button" className="purchase-retry" onClick={load}>
                다시 시도
              </button>
            </>
          ) : items === null ? (
            <p className="purchase-state" role="status">
              구매 내역을 불러오는 중…
            </p>
          ) : items.length === 0 ? (
            <p className="purchase-state">
              아직 구매한 내역이 없어요.
              <br />
              상점에서 알이나 가구를 들이면 여기에 쌓여요.
            </p>
          ) : (
            <ul className="purchase-list">
              {items.map((item) => (
                <li key={item.key} className="purchase-row">
                  <span className="purchase-thumb" aria-hidden>
                    {item.kind === "egg" ? (
                      <img src={item.image} alt="" className="purchase-thumb-image" />
                    ) : (
                      <FurnitureArt
                        category={item.category}
                        name=""
                        className="purchase-thumb-image"
                        emojiClassName="purchase-thumb-emoji"
                      />
                    )}
                  </span>
                  <div className="purchase-info">
                    <span className="purchase-name">{item.name}</span>
                    <div className="purchase-meta">
                      <span className={`purchase-kind purchase-kind--${item.kind}`}>
                        {item.kind === "egg" ? "알" : "가구"}
                      </span>
                      <span>{dateOf(item.at)}</span>
                      <span aria-hidden>·</span>
                      <span>{item.stage}</span>
                    </div>
                  </div>
                  <span className="purchase-price">{coin(item.price)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

export default PurchaseHistoryModal;
