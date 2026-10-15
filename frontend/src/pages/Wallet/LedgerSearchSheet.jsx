import { useEffect, useMemo, useRef, useState } from "react";
import Icon from "../../components/Icon";
import LedgerSheet from "./LedgerSheet";

// 내역 검색 — 거래처(내역 이름) · 메모 · 소비 사유 · 카테고리에서 찾는다.
// 월별 조회 API 만 있어서, 열릴 때 최근 SEARCH_MONTHS 개월을 한 번 불러와 브라우저에서 거른다.
// 행 모양은 목록 탭과 같은 renderRow 를 받아 쓴다.
const SEARCH_MONTHS = 6;
const WEEKDAY = ["일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"];

function longDateLabel(iso, todayYear) {
  const [y, m, d] = iso.split("-").map(Number);
  const weekday = WEEKDAY[new Date(y, m - 1, d).getDay()];
  return `${y !== todayYear ? `${y}년 ` : ""}${m}월 ${d}일 ${weekday}`;
}

/** 돋보기 — 공용 Icon 세트에 없어서 여기 둔다. 목록 탭 툴바 버튼도 같이 쓴다. */
export function SearchGlyph({ size = 20 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" aria-hidden="true" focusable="false">
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="M15.5 15.5 20 20" />
    </svg>
  );
}

function won(n) {
  return `${n.toLocaleString("ko-KR")}원`;
}

function LedgerSearchSheet({ loadMonth, today, renderRow, onClose }) {
  const inputRef = useRef(null);
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState(null); // null = 불러오는 중
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    const months = Array.from({ length: SEARCH_MONTHS }, (_, i) => {
      const d = new Date(today.getFullYear(), today.getMonth() - i, 1);
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    });
    Promise.allSettled(months.map(loadMonth)).then((results) => {
      if (!alive) return;
      const ok = results.filter((r) => r.status === "fulfilled").flatMap((r) => r.value);
      setFailed(results.every((r) => r.status === "rejected"));
      setRows(ok.sort((a, b) => b.dateIso.localeCompare(a.dateIso)));
    });
    return () => {
      alive = false;
    };
  }, [loadMonth, today]);

  const q = query.trim().toLowerCase();
  const groups = useMemo(() => {
    if (!q || !rows) return [];
    const hits = rows.filter((r) =>
      [r.name, r.memo, r.reason, r.catLabel].some((field) => (field || "").toLowerCase().includes(q)),
    );
    const out = [];
    hits.forEach((r) => {
      const last = out[out.length - 1];
      if (last && last.iso === r.dateIso) last.rows.push(r);
      else out.push({ iso: r.dateIso, rows: [r] });
    });
    return out;
  }, [q, rows]);
  const hitCount = groups.reduce((sum, g) => sum + g.rows.length, 0);

  return (
    <LedgerSheet
      ariaLabel="내역 검색"
      onClose={onClose}
      initialFocusRef={inputRef}
      className="lw-sheet--search"
      header={
        <header className="lw-search-head">
          <button type="button" className="hm-icon-btn" aria-label="검색 닫기" onClick={onClose}>
            <Icon name="chevronLeft" size={20} />
          </button>
          <input
            ref={inputRef}
            type="search"
            className="lw-search-input"
            placeholder="거래처 및 메모 내용을 입력하세요."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="내역 검색"
          />
        </header>
      }
    >
      {!q ? (
        <div className="lw-search-intro">
          <span className="lw-search-intro-icon" aria-hidden>
            <SearchGlyph size={28} />
          </span>
          <p className="lw-search-intro-title">수입·지출 내역 또는 메모 내용을 손쉽게 찾을 수 있어요</p>
          <p className="lw-search-intro-eg">예) 스타벅스</p>
        </div>
      ) : rows == null ? (
        <p className="lw-cal-state">내역을 불러오는 중…</p>
      ) : failed ? (
        <p className="lw-cal-state lw-cal-state--error">내역을 불러오지 못했어요.</p>
      ) : hitCount === 0 ? (
        <div className="hm-empty">
          <p>&lsquo;{query.trim()}&rsquo;에 맞는 내역이 없어요.</p>
        </div>
      ) : (
        <>
          <p className="lw-list-summary" aria-live="polite">
            최근 {SEARCH_MONTHS}개월에서 {hitCount}건
          </p>
          {groups.map((g) => {
            const income = g.rows.filter((r) => r.type === "INCOME").reduce((s, r) => s + r.amountNum, 0);
            const expense = g.rows.filter((r) => r.type === "EXPENSE").reduce((s, r) => s + r.amountNum, 0);
            return (
              <div key={g.iso} className="lw-list-group">
                <div className="lw-list-group-head">
                  <span>{longDateLabel(g.iso, today.getFullYear())}</span>
                  <span className="lw-list-group-sums">
                    {income > 0 && <span className="lw-amt-in">+{won(income)}</span>}
                    {expense > 0 && <span>-{won(expense)}</span>}
                  </span>
                </div>
                <ul className="hm-tx-list">{g.rows.map(renderRow)}</ul>
              </div>
            );
          })}
        </>
      )}
    </LedgerSheet>
  );
}

export default LedgerSearchSheet;
