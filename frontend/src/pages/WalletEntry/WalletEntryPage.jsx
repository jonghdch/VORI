import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import StepIndicator from "./StepIndicator";
import {
  CATEGORIES_BY_TYPE,
  PAYMENT_METHODS,
  categorize,
  formatToday,
  isPastDate,
  parseIsoDate,
  toIsoDate,
} from "./utils";
import { categorizeRemote } from "../../api/categorize";
import {
  createExpense,
  createIncome,
  createSaving,
  listCategoryTree,
  listExpensesByDate,
  listIncomesByDate,
  listSavingsByDate,
} from "../../api/ledger";
import { loadUserSettings } from "../Settings/SettingsPage";
import "./WalletEntry.css";

// Step 1 — 가계부 작성. 날짜는 다른 페이지에서 정해 ?date= 쿼리로 진입.
function WalletEntryPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const dateStr = params.get("date") || toIsoDate();
  const past = isPastDate(dateStr);

  // 같은 날짜의 입력값을 sessionStorage 에 보관 — Step 2/3 갔다 와도 유지.
  // 성공적으로 POST 한 뒤에는 clearDraft() 로 비움 (중복 저장 방지).
  const storageKey = `ledger-entry-${dateStr}`;
  const loadDraft = () => {
    try {
      const raw = sessionStorage.getItem(storageKey);
      if (!raw) return null;
      return JSON.parse(raw);
    } catch {
      return null;
    }
  };
  const draft = loadDraft();

  // 행 id 시퀀스 — 복원된 데이터가 있으면 그 max id 다음, 없으면 1 부터.
  const draftRows = (draft?.income || []).concat(
    draft?.expense || [],
    draft?.savings || [],
  );
  const maxDraftId = draftRows.reduce((m, r) => Math.max(m, r.id || 0), 0);
  const nextId = useRef(1 + maxDraftId);
  // 사용자 설정의 기본 결제수단 — 없으면 CREDIT
  const defaultPayment =
    loadUserSettings().defaultPaymentMethod || "CREDIT";
  const newRow = () => ({
    id: nextId.current++,
    paymentMethod: defaultPayment,
    name: "",
    amount: "",
  });

  // 초기엔 빈 행을 강제하지 않음 (행 0개로 시작). 사용자가 "+ 추가"로 직접 넣음.
  const [income, setIncome] = useState(draft?.income || []);
  const [expense, setExpense] = useState(draft?.expense || []);
  const [savings, setSavings] = useState(draft?.savings || []);

  // 지출 카테고리 편집 드롭다운 옵션 — 백엔드 카테고리 트리를 평면화 (value=leafId).
  const [expenseCatOptions, setExpenseCatOptions] = useState([]);
  useEffect(() => {
    let alive = true;
    listCategoryTree()
      .then((tree) => {
        if (!alive) return;
        const opts = [];
        (tree || []).forEach((p) =>
          (p.children || []).forEach((leaf) =>
            opts.push({ value: leaf.id, label: `${p.name} · ${leaf.name}` }),
          ),
        );
        setExpenseCatOptions(opts);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 변경마다 저장
  useEffect(() => {
    try {
      sessionStorage.setItem(
        storageKey,
        JSON.stringify({ income, expense, savings }),
      );
    } catch {}
  }, [income, expense, savings, storageKey]);

  // draft 비우는 helper. 성공적으로 POST 한 뒤 호출 → 다음 mount 에서는 DB fetch 로 폼 채움.
  const clearDraft = () => {
    try {
      sessionStorage.removeItem(storageKey);
    } catch {}
  };

  // mount (또는 dateStr 변경) 시 DB 에서 그 날짜 기존 데이터 fetch.
  // draft 에 실제 입력값이 있으면 draft 우선. 빈 draft (기본 빈 행만) 은 fetch.
  useEffect(() => {
    // mount 시점의 closure draft 가 아니라 fresh 한 sessionStorage 를 다시 읽음.
    // (직전 setState 가 즉시 sessionStorage 에 write 되므로 closure 는 stale)
    const fresh = loadDraft();
    const hasContent = (rows) =>
      Array.isArray(rows) &&
      rows.some(
        (r) => (r.name && r.name.trim()) || (r.amount && String(r.amount).trim()),
      );
    const draftHasContent =
      fresh &&
      (hasContent(fresh.income) ||
        hasContent(fresh.expense) ||
        hasContent(fresh.savings));
    if (draftHasContent) return;
    let cancelled = false;
    (async () => {
      try {
        const [exps, incs, savs] = await Promise.all([
          listExpensesByDate(dateStr),
          listIncomesByDate(dateStr),
          listSavingsByDate(dateStr),
        ]);
        if (cancelled) return;
        if (exps.length + incs.length + savs.length === 0) return;
        const next = (e) => nextId.current++;
        setExpense(
          exps.map((e) => ({
            id: next(),
            dbId: e.id,
            paymentMethod: e.paymentMethod || "CREDIT",
            name: e.item,
            amount: String(e.amount),
            categoryId: e.categoryId,
            categoryTouched: true, // 저장된 카테고리 — 자동분류로 덮지 않음
          })),
        );
        setIncome(
          incs.map((i) => ({
            id: next(),
            dbId: i.id,
            name: i.item,
            amount: String(i.amount),
            categoryEnum: i.source,
            sourceTouched: true,
          })),
        );
        setSavings(
          savs.map((s) => ({
            id: next(),
            dbId: s.id,
            name: s.item,
            amount: String(s.amount),
            categoryEnum: s.savingType,
            sourceTouched: true,
          })),
        );
      } catch {
        // fetch 실패는 무시 — 빈 폼으로 시작
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dateStr]);


  const addRow = (setter) => setter((rows) => [...rows, newRow()]);
  const removeRow = (setter, id) =>
    setter((rows) => rows.filter((r) => r.id !== id));
  const updateRow = (setter, id, patch) =>
    setter((rows) => rows.map((r) => (r.id === id ? { ...r, ...patch } : r)));

  // 빈 행(내역·금액 둘 다 비어있음)은 제출 시 무시한다 → 강제 삭제 불필요.
  const isRowEmpty = (r) =>
    !(r.name && r.name.trim()) && !(r.amount && String(r.amount).trim());
  const isRowComplete = (r) =>
    r.name && r.name.trim().length > 0 && r.amount && String(r.amount).length > 0;
  const allRows = [...income, ...expense, ...savings];
  const filledRows = allRows.filter((r) => !isRowEmpty(r));
  // 비어있지 않은 지출 행이 자동 분류 중이면 대기.
  const isCategorizing = expense.some((r) => !isRowEmpty(r) && r.categorizing);
  // 채워진 행이 하나 이상 있고, 그 행들이 전부 완성됐을 때만 진행.
  const canProceed =
    filledRows.length > 0 && filledRows.every(isRowComplete) && !isCategorizing;

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  // 백엔드에 모든 행 저장. 한 건이라도 실패하면 전체 중단하고 에러 표시.
  // 성공한 행에는 즉시 dbId 를 박아 retry 시 중복 저장 방지.
  const goNext = async () => {
    if (!canProceed || submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      for (const r of expense) {
        if (r.dbId || isRowEmpty(r)) continue; // 이미 저장됐거나 빈 행 — skip
        if (!r.categoryId) {
          throw new Error(`"${r.name}" 카테고리를 분류하지 못했어요. 잠시 후 다시 시도해주세요.`);
        }
        const saved = await createExpense({
          item: r.name.trim(),
          amount: r.amount,
          categoryId: r.categoryId,
          paymentMethod: r.paymentMethod,
          spentAt: `${dateStr}T00:00:00`,
        });
        setExpense((rows) =>
          rows.map((rr) => (rr.id === r.id ? { ...rr, dbId: saved.id } : rr)),
        );
      }
      for (const r of income) {
        if (r.dbId || isRowEmpty(r)) continue;
        const saved = await createIncome({
          item: r.name.trim(),
          amount: r.amount,
          source: r.categoryEnum || "OTHER",
          paymentMethod: null, // 수입은 결제수단 없음 — 출처(source)만
          receivedAt: dateStr,
        });
        setIncome((rows) =>
          rows.map((rr) => (rr.id === r.id ? { ...rr, dbId: saved.id } : rr)),
        );
      }
      for (const r of savings) {
        if (r.dbId || isRowEmpty(r)) continue;
        const saved = await createSaving({
          item: r.name.trim(),
          amount: r.amount,
          savingType: r.categoryEnum || "DEPOSIT",
          savedAt: dateStr,
        });
        setSavings((rows) =>
          rows.map((rr) => (rr.id === r.id ? { ...rr, dbId: saved.id } : rr)),
        );
      }
      // 성공 — draft 비움. 다음 mount 에서는 DB fetch 로 폼 채움 (정확한 dbId 포함).
      clearDraft();
      const qs = `?date=${dateStr}`;
      if (past) navigate(`/wallet/new/confirm${qs}`);
      else navigate(`/wallet/new/analysis${qs}`);
    } catch (e) {
      setSubmitError(e.message || "저장 중 오류가 발생했어요");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="ledger">
      <header className="ledger-header">
        <button
          type="button"
          className="ledger-logo-btn"
          onClick={() => navigate("/home")}
          aria-label="VORI 홈으로"
        >
          VORI
        </button>
      </header>

      <main className="ledger-main">
        <StepIndicator current={1} includeAnalysis={!past} />

        <div className="ledger-title-block">
          <h1 className="ledger-title">
            {formatToday(parseIsoDate(dateStr))}
          </h1>
          <p className="ledger-subtitle">
            {past
              ? "이전 날짜의 지출과 수입을 입력해주세요."
              : "오늘의 지출과 수입을 입력해주세요. 사진을 올리면 자동으로 채워져요."}
          </p>
        </div>

        {!past && (
          <button type="button" className="ledger-upload">
            이미지 업로드
          </button>
        )}

        {income.length > 0 && (
          <section className="ledger-section">
            <div className="ledger-section-title">수입</div>
            {income.map((row, idx) => (
              <EntryRow
                key={row.id}
                num={idx + 1}
                row={row}
                type="income"
                onChange={(patch) => updateRow(setIncome, row.id, patch)}
                onDelete={() => removeRow(setIncome, row.id)}
              />
            ))}
          </section>
        )}

        {expense.length > 0 && (
          <section className="ledger-section">
            <div className="ledger-section-title">지출</div>
            {expense.map((row, idx) => (
              <EntryRow
                key={row.id}
                num={idx + 1}
                row={row}
                type="expense"
                expenseCatOptions={expenseCatOptions}
                onChange={(patch) => updateRow(setExpense, row.id, patch)}
                onDelete={() => removeRow(setExpense, row.id)}
              />
            ))}
          </section>
        )}

        {savings.length > 0 && (
          <section className="ledger-section">
            <div className="ledger-section-title">저축</div>
            {savings.map((row, idx) => (
              <EntryRow
                key={row.id}
                num={idx + 1}
                row={row}
                type="savings"
                onChange={(patch) => updateRow(setSavings, row.id, patch)}
                onDelete={() => removeRow(setSavings, row.id)}
              />
            ))}
          </section>
        )}

        <div className="ledger-add-row">
          <button
            type="button"
            className="ledger-add-btn"
            onClick={() => addRow(setIncome)}
          >
            + 수입 추가하기
          </button>
          <button
            type="button"
            className="ledger-add-btn"
            onClick={() => addRow(setExpense)}
          >
            + 지출 추가하기
          </button>
          <button
            type="button"
            className="ledger-add-btn"
            onClick={() => addRow(setSavings)}
          >
            + 저축 추가하기
          </button>
        </div>

        <div className="ledger-actions">
          {!canProceed && (
            <p className="ledger-hint">
              {isCategorizing
                ? "지출 카테고리를 자동 분류하는 중이에요…"
                : "모든 항목의 내역과 금액을 입력해주세요"}
            </p>
          )}
          {submitError && <p className="ledger-hint">{submitError}</p>}
          <div className="ledger-actions-row">
            <button
              type="button"
              className="ledger-back"
              onClick={() => navigate("/home")}
              disabled={submitting}
            >
              돌아가기
            </button>
            <button
              type="button"
              className="ledger-next"
              onClick={goNext}
              disabled={!canProceed || submitting}
            >
              {submitting ? "저장 중…" : "다음 단계"}
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}

function EntryRow({ num, row, type, expenseCatOptions = [], onChange, onDelete }) {
  // 이름이 바뀌면 자동 분류로 카테고리/출처를 "제안"한다.
  // 단, 사용자가 드롭다운에서 직접 고른 경우(*Touched)엔 그 선택을 덮지 않는다.
  useEffect(() => {
    const name = (row.name || "").trim();
    if (!name) {
      onChange({
        categoryId: null,
        categoryEnum: null,
        categorizing: false,
        categoryTouched: false,
        sourceTouched: false,
      });
      return;
    }
    if (type !== "expense") {
      // income/savings — 로컬 키워드 룰로 출처/유형 제안 (미선택 시에만)
      if (row.sourceTouched) return;
      onChange({ categoryEnum: categorize(name, type), categorizing: false });
      return;
    }
    // expense — 사용자가 직접 고른 경우 자동 분류로 덮지 않음
    if (row.categoryTouched) {
      onChange({ categorizing: false });
      return;
    }
    // 백엔드 분류(Gemini). debounce 400ms. 진행 중엔 categorizing=true.
    onChange({ categorizing: true });
    let cancelled = false;
    const handle = setTimeout(async () => {
      const r = await categorizeRemote(name);
      if (cancelled) return;
      onChange(
        r == null
          ? { categoryId: null, categorizing: false }
          : { categoryId: r.leafId, categorizing: false },
      );
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [row.name, type]);

  // 카테고리/출처 선택 컨트롤 (눌러서 수정 가능한 드롭다운).
  const categoryControl =
    type === "expense" ? (
      <Dropdown
        value={row.categoryId ?? null}
        options={expenseCatOptions}
        onChange={(v) => onChange({ categoryId: v, categoryTouched: true })}
        placeholder={row.categorizing ? "분류 중…" : "카테고리"}
        align="right"
      />
    ) : (
      <Dropdown
        value={row.categoryEnum ?? null}
        options={CATEGORIES_BY_TYPE[type]}
        onChange={(v) => onChange({ categoryEnum: v, sourceTouched: true })}
        placeholder={type === "income" ? "수입 출처" : "저축 유형"}
        align="right"
      />
    );

  return (
    <div className="ledger-row">
      <div className="ledger-row-head">
        <span className="ledger-row-num">{String(num).padStart(2, "0")}</span>
        {/* 결제수단은 지출만. 수입은 "어디서 받았나"(출처), 저축은 유형만 고른다. */}
        {type === "expense" && (
          <Dropdown
            value={row.paymentMethod}
            options={PAYMENT_METHODS}
            onChange={(v) => onChange({ paymentMethod: v })}
            placeholder="결제수단"
          />
        )}
        <span className="ledger-row-spacer" />
        {categoryControl}
        <button
          type="button"
          className="ledger-row-del"
          aria-label="삭제"
          onClick={onDelete}
        >
          ×
        </button>
      </div>
      <div className="ledger-row-field">
        <span className="ledger-row-label">내역</span>
        <input
          type="text"
          className="ledger-row-input"
          value={row.name}
          onChange={(e) => onChange({ name: e.target.value })}
          placeholder={
            type === "income"
              ? "예: 6월 월급, 엄마 용돈"
              : "예: 스타벅스, GS25, 지하철"
          }
        />
      </div>
      <div className="ledger-row-field">
        <span className="ledger-row-label">금액</span>
        <div className="ledger-row-input-wrap">
          <input
            type="text"
            inputMode="numeric"
            className="ledger-row-input"
            value={row.amount ? Number(row.amount).toLocaleString("ko-KR") : ""}
            onChange={(e) =>
              onChange({ amount: e.target.value.replace(/[^\d]/g, "") })
            }
          />
          <span className="ledger-row-unit">원</span>
        </div>
      </div>
    </div>
  );
}

// 클릭 chip + 펼침 메뉴 (결제수단용).
function Dropdown({ value, options, onChange, placeholder, align = "left" }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const onDocMouseDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    const onEsc = (e) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDocMouseDown);
    document.addEventListener("keydown", onEsc);
    return () => {
      document.removeEventListener("mousedown", onDocMouseDown);
      document.removeEventListener("keydown", onEsc);
    };
  }, [open]);

  const selected = options.find((o) => o.value === value);
  const label = selected?.label || placeholder;

  return (
    <div className="ledger-dropdown" ref={wrapRef}>
      <button
        type="button"
        className={`ledger-chip ${selected ? "ledger-chip-selected" : ""}`}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className="ledger-chip-text">{label}</span>
        <span className="ledger-chip-caret" aria-hidden>
          ▾
        </span>
      </button>
      {open && (
        <ul
          className={`ledger-dropdown-menu ledger-dropdown-menu-${align}`}
          role="listbox"
        >
          {options.length === 0 && (
            <li className="ledger-dropdown-empty">불러오는 중…</li>
          )}
          {options.map((o) => (
            <li key={o.value}>
              <button
                type="button"
                className={`ledger-dropdown-option ${
                  o.value === value ? "ledger-dropdown-option-selected" : ""
                }`}
                onClick={() => {
                  onChange(o.value);
                  setOpen(false);
                }}
                role="option"
                aria-selected={o.value === value}
              >
                {o.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default WalletEntryPage;
