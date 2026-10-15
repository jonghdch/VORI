import { useEffect, useMemo, useState } from "react";
import { getSpendingPlan } from "../../api/spendingPlan";
import "./BudgetCard.css";

const won = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
const GROUPS = [
  { type: "ENERGY", label: "식비 · 카페", icon: "🍚", color: "energy" },
  { type: "CHARM", label: "쇼핑 · 뷰티", icon: "🛍️", color: "charm" },
  { type: "IQ", label: "문화 · 여가", icon: "🎟️", color: "iq" },
  { type: "ENDURANCE", label: "생활 · 고정비", icon: "🏠", color: "endurance" },
];

function BudgetCard({ yearMonth, spent, expenses }) {
  const [plan, setPlan] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (spent == null) return undefined;
    let active = true;
    setPlan(null);
    setLoadError(null);
    getSpendingPlan(yearMonth)
      .then((response) => active && setPlan(response))
      .catch((error) => active && setLoadError(error.message || "사용 가능 예산을 불러오지 못했어요."));
    return () => { active = false; };
  }, [yearMonth, spent, attempt]);

  const monthLabel = `${Number(yearMonth.slice(5))}월`;
  const totalBudget = Object.values(plan?.budgets || {}).reduce((sum, amount) => sum + Number(amount || 0), 0);
  const used = Number(spent || 0);
  const remaining = totalBudget - used;
  const groupUsage = useMemo(() => {
    const values = {};
    (expenses || []).forEach((expense) => {
      if (!expense.statType) return;
      values[expense.statType] = (values[expense.statType] || 0) + Number(expense.amountNum || 0);
    });
    return values;
  }, [expenses]);

  let body;
  if (loadError) {
    body = <div className="ledger-budget-state" role="alert"><p className="ledger-budget-label">{loadError}</p><button type="button" className="home-link-btn" onClick={() => setAttempt((value) => value + 1)}>다시 시도</button></div>;
  } else if (!plan || expenses == null) {
    body = <p className="ledger-card-empty">불러오는 중…</p>;
  } else if (totalBudget <= 0) {
    body = <div className="ledger-budget-state"><p className="ledger-budget-label">환경설정의 프로필에서 월 예산을 입력하면 카테고리별 사용 가능 예산이 표시돼요.</p></div>;
  } else {
    body = <>
      <div className="ledger-budget-total">
        <div><p className="ledger-budget-label">{monthLabel} 총 사용 가능 예산</p><p className="ledger-budget-values">{won(totalBudget)}</p></div>
        <span className={`ledger-budget-total-remaining${remaining < 0 ? " is-over" : ""}`}>{remaining < 0 ? `${won(-remaining)} 초과` : `${won(remaining)} 남음`}</span>
      </div>
      <div className="ledger-budget-groups">
        {GROUPS.map((group) => {
          const budget = Number(plan.budgets?.[group.type] || 0);
          const groupUsed = Number(groupUsage[group.type] || 0);
          const groupRemaining = budget - groupUsed;
          const percent = budget > 0 ? Math.round((groupUsed / budget) * 100) : 0;
          const width = Math.min(Math.max(percent, 0), 100);
          return <article key={group.type} className={`ledger-budget-group ledger-budget-group--${group.color}`}>
            <div className="ledger-budget-group-head"><strong><span aria-hidden>{group.icon}</span>{group.label}</strong><span>{won(budget)}</span></div>
            <div className="ledger-budget-track" role="progressbar" aria-label={`${group.label} 예산 사용률`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={width} aria-valuetext={`${percent}%`}><div className={`ledger-budget-fill${groupRemaining < 0 ? " is-over" : ""}`} style={{ width: `${width}%` }} /></div>
            <p className="ledger-budget-meta ledger-budget-foot"><span>{won(groupUsed)} 사용 · {percent}%</span><span className={groupRemaining < 0 ? "ledger-budget-over" : ""}>{groupRemaining < 0 ? `${won(-groupRemaining)} 초과` : `${won(groupRemaining)} 남음`}</span></p>
          </article>;
        })}
      </div>
      <p className="ledger-budget-label ledger-budget-note">환경설정에서 직접 정한 이번 달 가계부 예산이에요.</p>
    </>;
  }

  return <section className="home-card ledger-budget-card ledger-sec--sky"><div className="ledger-budget-head"><h2 className="home-card-title home-card-title--sm ledger-sec-title"><span className="ledger-sec-icon" aria-hidden>🎯</span>총 사용 가능 예산</h2></div>{body}</section>;
}

export default BudgetCard;
