import { useEffect, useState } from "react";
import { getSpendingPlan } from "../../api/spendingPlan";

const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;

// 가계부에서 별도의 수동 예산을 입력하지 않는다.
// 프로필 수입·고정비·설문을 바탕으로 계산된 스탯별 예산의 합계를 보여 준다.
function BudgetCard({ yearMonth, spent }) {
  const [plan, setPlan] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (spent == null) return undefined;
    let active = true;
    setPlan(null);
    setLoadError(null);

    getSpendingPlan(yearMonth)
      .then((response) => {
        if (active) setPlan(response);
      })
      .catch((error) => {
        if (active) setLoadError(error.message || "사용 가능 예산을 불러오지 못했어요.");
      });

    return () => {
      active = false;
    };
  }, [yearMonth, spent, attempt]);

  const monthLabel = `${Number(yearMonth.slice(5))}월`;
  const totalBudget = Object.values(plan?.budgets || {}).reduce(
    (sum, amount) => sum + Number(amount || 0),
    0,
  );
  const used = Number(spent || 0);
  const remaining = totalBudget - used;
  const usagePct = totalBudget > 0 ? Math.round((used / totalBudget) * 100) : 0;
  const progressPct = Math.min(Math.max(usagePct, 0), 100);

  let body;
  if (loadError) {
    body = (
      <div className="ledger-budget-state" role="alert">
        <p className="ledger-budget-label">{loadError}</p>
        <button type="button" className="home-link-btn" onClick={() => setAttempt((value) => value + 1)}>
          다시 시도
        </button>
      </div>
    );
  } else if (!plan) {
    body = <p className="ledger-card-empty">불러오는 중…</p>;
  } else if (totalBudget <= 0) {
    body = (
      <div className="ledger-budget-state">
        <p className="ledger-budget-label">
          월수입을 입력하면 고정비와 예비비를 반영한 총 사용 가능 예산이 표시돼요.
        </p>
      </div>
    );
  } else {
    body = (
      <>
        <p className="ledger-budget-label">{monthLabel} 총 사용 가능 예산</p>
        <p className="ledger-budget-values">{won(totalBudget)}</p>
        <div
          className="ledger-budget-track"
          role="progressbar"
          aria-label={`${monthLabel} 총 사용 가능 예산 사용률`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={progressPct}
          aria-valuetext={`${usagePct}%`}
        >
          <div className={`ledger-budget-fill${remaining < 0 ? " is-over" : ""}`} style={{ width: `${progressPct}%` }} />
        </div>
        <p className="ledger-budget-meta ledger-budget-foot">
          <span>{won(used)} 사용 · {usagePct}%</span>
          {remaining < 0 ? (
            <span className="ledger-budget-over">{won(-remaining)} 초과</span>
          ) : (
            <span>{won(remaining)} 남음</span>
          )}
        </p>
        <p className="ledger-budget-label" style={{ marginTop: 10, marginBottom: 0 }}>
          월수입에서 고정비와 예비비를 제외한 금액이에요.
        </p>
      </>
    );
  }

  return (
    <section className="home-card ledger-budget-card ledger-sec--sky">
      <div className="ledger-budget-head">
        <h2 className="home-card-title home-card-title--sm ledger-sec-title">
          <span className="ledger-sec-icon" aria-hidden>🎯</span>
          총 사용 가능 예산
        </h2>
      </div>
      {body}
    </section>
  );
}

export default BudgetCard;
