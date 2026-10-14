import { useState } from "react";
import Icon from "../../components/Icon";
import { issuerOf } from "../../constants/issuers";
import LedgerSheet from "./LedgerSheet";

// 목록 탭 내역 필터 — 거래수단 · 카테고리(지출/수입). 고친 값은 [적용]을 눌러야 반영된다.
// 기간은 화면 위 [주|월] 이 맡아서 여기엔 없다. 이체는 VORI 에 없는 거래 종류라 뺐다.
// value = { instrumentId: number|null, types: {EXPENSE, INCOME}, categories: string[]|null, incomeSources: string[]|null }
// categories = 지출 카테고리 이름, incomeSources = 수입 출처 enum(ALLOWANCE …). null 이면 전체.
export const EMPTY_LIST_FILTER = {
  instrumentId: null,
  types: { EXPENSE: true, INCOME: true },
  categories: null,
  incomeSources: null,
};

export function isListFilterActive(f) {
  return (
    f.instrumentId != null || !f.types.EXPENSE || !f.types.INCOME || f.categories != null || f.incomeSources != null
  );
}

// 지출·수입 한 줄 — 체크박스 + 「전체 ›」 를 누르면 세부 칩이 펼쳐진다. options = [{ value, label }]
function TypeFilterRow({ label, checked, onCheck, options, selected, onChangeSelected }) {
  const [open, setOpen] = useState(selected != null);
  const picked = selected ?? options.map((o) => o.value);
  const toggle = (value) => {
    const set = new Set(picked);
    if (set.has(value)) set.delete(value);
    else set.add(value);
    const list = options.map((o) => o.value).filter((v) => set.has(v));
    onChangeSelected(list.length === options.length ? null : list);
  };
  const summary = selected == null ? "전체" : selected.length === 0 ? "선택 안 함" : `${selected.length}개`;
  return (
    <>
      <div className="lw-filter-check-row">
        <label className="lw-filter-check">
          <input type="checkbox" checked={checked} onChange={(e) => onCheck(e.target.checked)} />
          <span>{label}</span>
        </label>
        <button
          type="button"
          className="lw-filter-more"
          aria-expanded={open}
          disabled={!checked}
          onClick={() => setOpen((v) => !v)}
        >
          {summary}
          <Icon name={open ? "chevronDown" : "chevronRight"} size={14} />
        </button>
      </div>
      {open && checked && (
        <div className="lw-filter-chips lw-filter-chips--cats" role="group" aria-label={`${label} 카테고리`}>
          {options.map((o) => {
            const on = picked.includes(o.value);
            return (
              <button
                key={o.value}
                type="button"
                aria-pressed={on}
                className={`lw-filter-chip ${on ? "is-on" : ""}`}
                onClick={() => toggle(o.value)}
              >
                {o.label}
              </button>
            );
          })}
        </div>
      )}
    </>
  );
}

function LedgerFilterSheet({ value, instruments, categoryOptions, incomeOptions, onApply, onClose }) {
  const [draft, setDraft] = useState(value);
  // [초기화] 뒤 펼침 상태도 처음으로 — key 를 바꿔 줄을 새로 그린다
  const [resetKey, setResetKey] = useState(0);
  const patch = (next) => setDraft((d) => ({ ...d, ...next }));

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
          <button
            type="button"
            className="hm-btn lw-btn-soft"
            onClick={() => {
              setDraft(EMPTY_LIST_FILTER);
              setResetKey((k) => k + 1);
            }}
          >
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
      <div className="lw-filter-box" key={resetKey}>
        <TypeFilterRow
          label="지출"
          checked={draft.types.EXPENSE}
          onCheck={(on) => patch({ types: { ...draft.types, EXPENSE: on } })}
          options={categoryOptions.map((c) => ({ value: c, label: c }))}
          selected={draft.categories}
          onChangeSelected={(categories) => patch({ categories })}
        />
        <TypeFilterRow
          label="수입"
          checked={draft.types.INCOME}
          onCheck={(on) => patch({ types: { ...draft.types, INCOME: on } })}
          options={incomeOptions}
          selected={draft.incomeSources}
          onChangeSelected={(incomeSources) => patch({ incomeSources })}
        />
      </div>
    </LedgerSheet>
  );
}

export default LedgerFilterSheet;
