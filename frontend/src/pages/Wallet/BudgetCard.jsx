import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { deleteBudget, getBudget, saveBudget } from "../../api/budget";

const won = (n) => `${Number(n).toLocaleString("ko-KR")}원`;
// 예산 칸은 숫자만, 9자리(9억 원대)까지. 서버 금액이 int 라 10자리는 범위를 넘을 수 있다.
const MAX_DIGITS = 9;

// 가계부의 "예산 현황" 카드 — 그 달 예산의 조회·설정·변경·해제.
//
// spent 는 가계부가 보여 주는 그 달 지출 합계다. 값이 바뀌면(지출 삭제 등) 예산 현황도 다시 읽는다.
// null 이면 가계부가 아직 불러오는 중이므로 기다린다 — 달을 넘길 때 요청이 두 번 나가지 않는다.
// 다른 화면의 "예산 설정" 바로가기는 /wallet#budget 으로 들어오고, 그때는 입력 칸을 바로 연다.
function BudgetCard({ yearMonth, spent }) {
  const [budget, setBudget] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [attempt, setAttempt] = useState(0);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);
  const sectionRef = useRef(null);
  const inputRef = useRef(null);
  const handledNav = useRef(null);
  // 마지막으로 보낸 조회의 번호. 저장·해제가 시작되면 올려서, 그 전에 나간 조회 응답이
  // 늦게 와도 저장 결과를 덮어쓰지 못하게 한다.
  const latestRequest = useRef(0);
  const { hash, key: navKey } = useLocation();

  // 달을 넘긴 직후에는 이전 달 응답이 남아 있다 — 지금 달 것이 아니면 없는 것으로 본다.
  const current = budget?.yearMonth === yearMonth ? budget : null;
  const monthLabel = `${Number(yearMonth.slice(5))}월`;

  useEffect(() => {
    if (spent == null) return undefined;
    const request = ++latestRequest.current;
    const isLatest = () => request === latestRequest.current;
    setLoadError(null);
    getBudget(yearMonth)
      .then((b) => isLatest() && setBudget(b))
      .catch((e) => isLatest() && setLoadError(e.message || "예산을 불러오지 못했어요."));
    return () => {
      // 달이 바뀌거나 화면을 떠나면 이 응답은 버린다.
      if (isLatest()) latestRequest.current += 1;
    };
  }, [yearMonth, spent, attempt]);

  useEffect(() => {
    setEditing(false);
    setFormError(null);
  }, [yearMonth]);

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  useEffect(() => {
    if (hash !== "#budget" || !current || handledNav.current === navKey) return;
    handledNav.current = navKey;
    sectionRef.current?.scrollIntoView({ block: "center" });
    setDraft(current.budgetSet ? String(current.amount) : "");
    setFormError(null);
    setEditing(true);
  }, [hash, navKey, current]);

  const openEditor = () => {
    setDraft(current?.budgetSet ? String(current.amount) : "");
    setFormError(null);
    setEditing(true);
  };

  const submit = async (e) => {
    e.preventDefault();
    if (saving) return;
    const amount = Number(draft);
    if (!draft || amount < 1) {
      setFormError("예산은 1원 이상으로 입력해 주세요.");
      inputRef.current?.focus();
      return;
    }
    setSaving(true);
    setFormError(null);
    latestRequest.current += 1;
    try {
      setBudget(await saveBudget(yearMonth, amount));
      setEditing(false);
    } catch (err) {
      setFormError(err.message || "예산을 저장하지 못했어요.");
    } finally {
      setSaving(false);
    }
  };

  const clear = async () => {
    if (saving) return;
    setSaving(true);
    setFormError(null);
    latestRequest.current += 1;
    try {
      await deleteBudget(yearMonth);
      setBudget({
        ...current,
        id: null,
        amount: 0,
        remaining: 0,
        usagePct: 0,
        exceeded: false,
        budgetSet: false,
      });
      setEditing(false);
    } catch (err) {
      setFormError(err.message || "예산을 해제하지 못했어요.");
    } finally {
      setSaving(false);
    }
  };

  let body;
  if (loadError) {
    body = (
      <div className="ledger-budget-state" role="alert">
        <p className="ledger-budget-label">{loadError}</p>
        <button type="button" className="home-link-btn" onClick={() => setAttempt((n) => n + 1)}>
          다시 시도
        </button>
      </div>
    );
  } else if (!current) {
    body = <p className="ledger-card-empty">불러오는 중…</p>;
  } else if (editing) {
    body = (
      <form className="ledger-budget-form" onSubmit={submit} noValidate>
        <label className="ledger-budget-label" htmlFor="ledger-budget-amount">
          {monthLabel} 예산
        </label>
        <div className="ledger-budget-field">
          <input
            ref={inputRef}
            id="ledger-budget-amount"
            className="ledger-budget-input"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            placeholder="0"
            value={draft ? Number(draft).toLocaleString("ko-KR") : ""}
            onChange={(e) => {
              setDraft(e.target.value.replace(/\D/g, "").replace(/^0+/, "").slice(0, MAX_DIGITS));
              setFormError(null);
            }}
            aria-invalid={formError ? true : undefined}
            aria-describedby={formError ? "ledger-budget-error" : undefined}
            disabled={saving}
          />
          <span className="ledger-budget-unit" aria-hidden>원</span>
        </div>
        {formError && (
          <p id="ledger-budget-error" className="ledger-budget-error" role="alert">
            {formError}
          </p>
        )}
        <div className="ledger-budget-actions">
          <button type="submit" className="home-btn home-btn-primary" disabled={saving}>
            {saving ? "저장 중…" : "저장"}
          </button>
          <button type="button" className="home-link-btn" onClick={() => setEditing(false)} disabled={saving}>
            취소
          </button>
          {current.budgetSet && (
            <button type="button" className="home-link-btn ledger-budget-clear" onClick={clear} disabled={saving}>
              예산 해제
            </button>
          )}
        </div>
      </form>
    );
  } else if (!current.budgetSet) {
    body = (
      <div className="ledger-budget-state">
        <p className="ledger-budget-label">
          {monthLabel} 예산을 아직 정하지 않았어요. 정해 두면 지출이 얼마나 찼는지 보여 드려요.
        </p>
        <button type="button" className="home-btn home-btn-primary" onClick={openEditor}>
          예산 정하기
        </button>
      </div>
    );
  } else {
    const pct = Math.min(current.usagePct, 100);
    body = (
      <>
        <p className="ledger-budget-label">
          {monthLabel} 예산 {won(current.amount)}
        </p>
        <p className="ledger-budget-values">{won(current.spent)} 사용</p>
        <div
          className="ledger-budget-track"
          role="progressbar"
          aria-label={`${monthLabel} 예산 사용률`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={pct}
          aria-valuetext={`${current.usagePct}%`}
        >
          <div
            className={`ledger-budget-fill${current.exceeded ? " is-over" : ""}`}
            style={{ width: `${pct}%` }}
          />
        </div>
        <p className="ledger-budget-meta ledger-budget-foot">
          <span>{current.usagePct}%</span>
          {current.exceeded ? (
            <span className="ledger-budget-over">{won(-current.remaining)} 초과</span>
          ) : (
            <span>{won(current.remaining)} 남음</span>
          )}
        </p>
      </>
    );
  }

  return (
    <section ref={sectionRef} id="budget" className="home-card ledger-budget-card ledger-sec--sky">
      <div className="ledger-budget-head">
        <h2 className="home-card-title home-card-title--sm ledger-sec-title">
          <span className="ledger-sec-icon" aria-hidden>🎯</span>
          예산 현황
        </h2>
        {current?.budgetSet && !editing && !loadError && (
          <button type="button" className="home-link-btn" onClick={openEditor}>
            예산 변경
          </button>
        )}
      </div>
      {body}
    </section>
  );
}

export default BudgetCard;
