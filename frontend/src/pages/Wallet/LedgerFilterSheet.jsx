import { useState } from "react";
import Icon from "../../components/Icon";
import { issuerOf } from "../../constants/issuers";
import LedgerSheet from "./LedgerSheet";

// 목록 탭 내역 필터 — 거래수단 · 카테고리(지출/수입). 고친 값은 [적용]을 눌러야 반영된다.
// 기간은 화면 위 [주|월] 이 맡아서 여기엔 없다. 이체는 VORI 에 없는 거래 종류라 뺐다.
// value = { instrumentId: number|null, types: {EXPENSE, INCOME}, categories: string[]|null }
// categories 가 null 이면 지출 카테고리 전체.
export const EMPTY_LIST_FILTER = { instrumentId: null, types: { EXPENSE: true, INCOME: true }, categories: null };

export function isListFilterActive(f) {
  return f.instrumentId != null || !f.types.EXPENSE || !f.types.INCOME || f.categories != null;
}

function LedgerFilterSheet({ value, instruments, categoryOptions, onApply, onClose }) {
  const [draft, setDraft] = useState(value);
  const [showCategories, setShowCategories] = useState(Boolean(value.categories));
  const patch = (next) => setDraft((d) => ({ ...d, ...next }));

  const selectedCats = draft.categories ?? categoryOptions;
  const toggleCategory = (cat) => {
    const set = new Set(selectedCats);
    if (set.has(cat)) set.delete(cat);
    else set.add(cat);
    const list = categoryOptions.filter((c) => set.has(c));
    patch({ categories: list.length === categoryOptions.length ? null : list });
  };
  const categorySummary =
    draft.categories == null ? "전체" : draft.categories.length === 0 ? "선택 안 함" : `${draft.categories.length}개`;

  const chip = (key, on, label, onClick, dot) => (
    <button
      key={key}
      type="button"
      role="radio"
      aria-checked={on}
      className={`lw-filter-chip ${on ? "is-on" : ""}`}
      onClick={onClick}
    >
      {on && <Icon name="check" size={13} strokeWidth={2.4} />}
      {dot && <span className="lw-list-dot" style={{ background: dot }} aria-hidden />}
      {label}
    </button>
  );

  return (
    <LedgerSheet
      title="내역 필터"
      onClose={onClose}
      footer={
        <div className="lw-sheet-actions">
          <button type="button" className="hm-btn lw-btn-soft" onClick={() => setDraft(EMPTY_LIST_FILTER)}>
            초기화
          </button>
          <button type="button" className="hm-btn hm-btn--dark" onClick={() => onApply(draft)}>
            적용
          </button>
        </div>
      }
    >
      <p className="lw-filter-label">거래수단</p>
      <div className="lw-filter-chips" role="radiogroup" aria-label="거래수단">
        {chip("all", draft.instrumentId == null, "모든 거래 수단", () => patch({ instrumentId: null }))}
        {instruments.map((i) =>
          chip(i.id, draft.instrumentId === i.id, i.name, () => patch({ instrumentId: i.id }), issuerOf(i.issuer).color),
        )}
      </div>

      <p className="lw-filter-label">카테고리</p>
      <div className="lw-filter-box">
        <div className="lw-filter-check-row">
          <label className="lw-filter-check">
            <input
              type="checkbox"
              checked={draft.types.EXPENSE}
              onChange={(e) => patch({ types: { ...draft.types, EXPENSE: e.target.checked } })}
            />
            <span>지출</span>
          </label>
          <button
            type="button"
            className="lw-filter-more"
            aria-expanded={showCategories}
            disabled={!draft.types.EXPENSE}
            onClick={() => setShowCategories((v) => !v)}
          >
            {categorySummary}
            <Icon name={showCategories ? "chevronDown" : "chevronRight"} size={14} />
          </button>
        </div>
        {showCategories && draft.types.EXPENSE && (
          <div className="lw-filter-chips lw-filter-chips--cats" role="group" aria-label="지출 카테고리">
            {categoryOptions.map((cat) => {
              const on = selectedCats.includes(cat);
              return (
                <button
                  key={cat}
                  type="button"
                  aria-pressed={on}
                  className={`lw-filter-chip ${on ? "is-on" : ""}`}
                  onClick={() => toggleCategory(cat)}
                >
                  {cat}
                </button>
              );
            })}
          </div>
        )}
        <div className="lw-filter-check-row">
          <label className="lw-filter-check">
            <input
              type="checkbox"
              checked={draft.types.INCOME}
              onChange={(e) => patch({ types: { ...draft.types, INCOME: e.target.checked } })}
            />
            <span>수입</span>
          </label>
          <span className="lw-filter-more lw-filter-more--static">전체</span>
        </div>
      </div>
    </LedgerSheet>
  );
}

export default LedgerFilterSheet;
