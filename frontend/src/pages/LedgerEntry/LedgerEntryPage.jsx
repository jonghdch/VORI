import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import StepIndicator from "./StepIndicator";
import {
  PAYMENT_METHODS,
  categorize,
  formatToday,
  getCategoryDisplayPath,
  isPastDate,
  parseIsoDate,
  toIsoDate,
} from "./utils";
import { categorizeRemote } from "../../api/categorize";
import {
  createExpense,
  createIncome,
  createSaving,
  listExpensesByDate,
  listIncomesByDate,
  listSavingsByDate,
} from "../../api/ledger";
import { loadUserSettings } from "../Settings/SettingsPage";
import "./LedgerEntry.css";

// Step 1 — 가계부 작성. 날짜는 다른 페이지에서 정해 ?date= 쿼리로 진입.
function LedgerEntryPage() {
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

  // 행 id 시퀀스 — 복원된 데이터가 있으면 그 다음부터 발급
  const nextId = useRef(
    1 +
      Math.max(
        0,
        ...((draft?.income || []).concat(draft?.expense || [], draft?.savings || []))
          .map((r) => r.id || 0),
        3,
      ),
  );
  // 사용자 설정의 기본 결제수단 — 없으면 CREDIT
  const defaultPayment =
    loadUserSettings().defaultPaymentMethod || "CREDIT";
  const newRow = () => ({
    id: nextId.current++,
    paymentMethod: defaultPayment,
    name: "",
    amount: "",
  });

  const [income, setIncome] = useState(
    draft?.income || [
      { id: 1, paymentMethod: defaultPayment, name: "", amount: "" },
    ],
  );
  const [expense, setExpense] = useState(
    draft?.expense || [
      { id: 2, paymentMethod: defaultPayment, name: "", amount: "" },
      { id: 3, paymentMethod: defaultPayment, name: "", amount: "" },
    ],
  );
  const [savings, setSavings] = useState(draft?.savings || []);

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
    const hasContent = (rows) =>
      Array.isArray(rows) &&
      rows.some(
        (r) => (r.name && r.name.trim()) || (r.amount && String(r.amount).trim()),
      );
    const draftHasContent =
      draft &&
      (hasContent(draft.income) ||
        hasContent(draft.expense) ||
        hasContent(draft.savings));
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
          })),
        );
        setIncome(
          incs.map((i) => ({
            id: next(),
            dbId: i.id,
            paymentMethod: i.paymentMethod || "CREDIT",
            name: i.item,
            amount: String(i.amount),
            categoryEnum: i.source,
          })),
        );
        setSavings(
          savs.map((s) => ({
            id: next(),
            dbId: s.id,
            paymentMethod: "CREDIT",
            name: s.item,
            amount: String(s.amount),
            categoryEnum: s.savingType,
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

  // 모든 행이 내역·금액 둘 다 채워졌는지. 빈 섹션은 무시.
  const isRowComplete = (r) =>
    r.name && r.name.trim().length > 0 && r.amount && r.amount.length > 0;
  const allRows = [...income, ...expense, ...savings];
  const canProceed = allRows.length === 0 || allRows.every(isRowComplete);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  // 백엔드에 모든 행 저장. 한 건이라도 실패하면 전체 중단하고 에러 표시.
  // 부분 실패 처리는 추후 — 지금은 단순 best-effort sequential.
  const goNext = async () => {
    if (!canProceed || submitting) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      for (const r of expense) {
        if (r.dbId) continue; // 이미 DB 에 있음 — skip
        if (!r.categoryId) {
          throw new Error(`"${r.name}" 카테고리를 분류하지 못했어요. 잠시 후 다시 시도해주세요.`);
        }
        await createExpense({
          item: r.name.trim(),
          amount: r.amount,
          categoryId: r.categoryId,
          paymentMethod: r.paymentMethod,
          spentAt: `${dateStr}T00:00:00`,
        });
      }
      for (const r of income) {
        if (r.dbId) continue;
        await createIncome({
          item: r.name.trim(),
          amount: r.amount,
          source: r.categoryEnum || "OTHER",
          paymentMethod: r.paymentMethod,
          receivedAt: dateStr,
        });
      }
      for (const r of savings) {
        if (r.dbId) continue;
        await createSaving({
          item: r.name.trim(),
          amount: r.amount,
          savingType: r.categoryEnum || "DEPOSIT",
          savedAt: dateStr,
        });
      }
      // 성공 — draft 비움. 다음 mount 에서는 DB fetch 로 폼 채움 (정확한 dbId 포함).
      clearDraft();
      const qs = `?date=${dateStr}`;
      if (past) navigate(`/ledger/new/confirm${qs}`);
      else navigate(`/ledger/new/analysis${qs}`);
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
            <p className="ledger-hint">모든 항목의 내역과 금액을 입력해주세요</p>
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

function EntryRow({ num, row, type, onChange, onDelete }) {
  // 카테고리 자동 분류 결과 (chip 에 표시할 라벨).
  // - expense: 백엔드 /api/categories/categorize (Gemini embedding) — debounce 후 호출
  // - income/savings: 로컬 키워드 룰 (백엔드에 income/savings 카테고리가 없어서 일단 유지)
  const [autoLabel, setAutoLabel] = useState(null);
  useEffect(() => {
    const name = (row.name || "").trim();
    if (!name) {
      setAutoLabel(null);
      onChange({ categoryId: null, categoryEnum: null });
      return;
    }
    if (type !== "expense") {
      const v = categorize(name, type);
      setAutoLabel(getCategoryDisplayPath(v, type));
      // income/savings 는 ENUM 값을 그대로 row 에 보관 (제출 시 source/savingType 로 사용)
      onChange({ categoryEnum: v, categoryId: null });
      return;
    }
    // expense — debounce 400ms 후 백엔드 호출
    let cancelled = false;
    const handle = setTimeout(async () => {
      const r = await categorizeRemote(name);
      if (cancelled) return;
      if (r == null) {
        setAutoLabel("기타");
        onChange({ categoryId: null, categoryEnum: null });
      } else {
        setAutoLabel(`${r.parentName} · ${r.leafName}`);
        onChange({ categoryId: r.leafId, categoryEnum: null });
      }
    }, 400);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [row.name, type]);

  return (
    <div className="ledger-row">
      <div className="ledger-row-head">
        <span className="ledger-row-num">{String(num).padStart(2, "0")}</span>
        <Dropdown
          value={row.paymentMethod}
          options={PAYMENT_METHODS}
          onChange={(v) => onChange({ paymentMethod: v })}
          placeholder="결제수단"
        />
        <span className="ledger-row-spacer" />
        <CategoryChip label={autoLabel} />
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
          placeholder="예: 스타벅스, GS25, 지하철"
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

// 자동 분류 결과 표시 전용 chip. 클릭 불가.
// label 이 null/undefined 면 "자동 분류" placeholder.
function CategoryChip({ label }) {
  if (!label) {
    return (
      <span className="ledger-chip ledger-chip-readonly ledger-chip-placeholder">
        자동 분류
      </span>
    );
  }
  return (
    <span className="ledger-chip ledger-chip-readonly ledger-chip-auto">
      {label}
    </span>
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
        {label}
      </button>
      {open && (
        <ul
          className={`ledger-dropdown-menu ledger-dropdown-menu-${align}`}
          role="listbox"
        >
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

export default LedgerEntryPage;
