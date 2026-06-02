import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import StepIndicator from "./StepIndicator";
import {
  formatToday,
  isPastDate,
  parseIsoDate,
  toIsoDate,
} from "./utils";
import {
  listCategoryTree,
  listExpensesByDate,
  listIncomesByDate,
  listSavingsByDate,
} from "../../api/ledger";
import "./WalletEntry.css";

const PAYMENT_LABELS = {
  CASH: "현금",
  DEBIT: "체크카드",
  CREDIT: "신용카드",
  TRANSFER: "계좌이체",
  MOBILE_PAY: "모바일페이",
};
const PAYMENT_ORDER = ["CASH", "DEBIT", "CREDIT", "TRANSFER", "MOBILE_PAY"];

const SAVING_TYPE_LABELS = { DEPOSIT: "예금", INVEST: "투자" };
const INCOME_SOURCE_LABELS = {
  ALLOWANCE: "용돈",
  PART_TIME: "알바",
  SCHOLARSHIP: "장학금",
  SIDE_JOB: "부수입",
  GIFT: "선물",
  INTEREST: "이자",
  OTHER: "기타",
};

// Step 3 — 확인. 그 날짜에 저장된 expenses/incomes/savings 를 결제수단별로 묶어 표시.
function WalletConfirmPage({ user }) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const dateStr = params.get("date") || toIsoDate();
  const past = isPastDate(dateStr);

  const [expenses, setExpenses] = useState([]);
  const [incomes, setIncomes] = useState([]);
  const [savings, setSavings] = useState([]);
  const [categoryMap, setCategoryMap] = useState({}); // id → {name, parentName}
  const [loading, setLoading] = useState(true);

  // 초기 fetch
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [exps, incs, savs, tree] = await Promise.all([
          listExpensesByDate(dateStr),
          listIncomesByDate(dateStr),
          listSavingsByDate(dateStr),
          listCategoryTree(),
        ]);
        if (cancelled) return;
        const map = {};
        for (const parent of tree || []) {
          map[parent.id] = { name: parent.name, parentName: null };
          for (const child of parent.children || []) {
            map[child.id] = { name: child.name, parentName: parent.name };
          }
        }
        setCategoryMap(map);
        setExpenses(exps);
        setIncomes(incs);
        setSavings(savs);
        setLoading(false);
      } catch {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [dateStr]);

  // 결제수단별 그룹핑
  const paymentGroups = useMemo(() => {
    const groups = {};
    for (const pm of PAYMENT_ORDER) {
      groups[pm] = { income: 0, expense: 0, items: [] };
    }
    for (const e of expenses) {
      const pm = e.paymentMethod || "CASH";
      if (!groups[pm]) groups[pm] = { income: 0, expense: 0, items: [] };
      groups[pm].expense += e.amount;
      const cat = categoryMap[e.categoryId];
      const catLabel = cat
        ? cat.parentName
          ? `${cat.parentName} · ${cat.name}`
          : cat.name
        : "기타";
      groups[pm].items.push({
        id: `e-${e.id}`,
        name: e.item,
        category: catLabel,
        amount: e.amount,
        sign: "-",
      });
    }
    for (const i of incomes) {
      const pm = i.paymentMethod || "CASH";
      if (!groups[pm]) groups[pm] = { income: 0, expense: 0, items: [] };
      groups[pm].income += i.amount;
      groups[pm].items.push({
        id: `i-${i.id}`,
        name: i.item,
        category: INCOME_SOURCE_LABELS[i.source] || "수입",
        amount: i.amount,
        sign: "+",
      });
    }
    return groups;
  }, [expenses, incomes, categoryMap]);

  // 비어있지 않은 그룹만
  const visiblePayments = PAYMENT_ORDER.filter((pm) => {
    const g = paymentGroups[pm];
    return g && (g.items.length > 0 || g.income > 0 || g.expense > 0);
  });

  // 저축은 별도 섹션 (결제수단 개념 없음)
  const savingTotal = savings.reduce((s, x) => s + x.amount, 0);

  // 기본 모두 펼침 — 한 화면에서 내역까지 같이 보이게.
  // 사용자가 카드 우측 토글로 접을 수 있음.
  const [collapsed, setCollapsed] = useState({}); // pm → bool (true 이면 접힘)
  const isOpen = (k) => !collapsed[k];
  const toggleOpen = (k) => setCollapsed((p) => ({ ...p, [k]: !p[k] }));

  const goBack = () => {
    const qs = `?date=${dateStr}`;
    if (past) navigate(`/wallet/new${qs}`);
    else navigate(`/wallet/new/analysis${qs}`);
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
        <StepIndicator current={past ? 2 : 3} includeAnalysis={!past} />

        <div className="ledger-title-block ledger-title-block-center">
          <h1 className="ledger-title">
            {formatToday(parseIsoDate(dateStr))}
          </h1>
          <p className="ledger-subtitle">
            저장된 내역을 확인하고 마무리해주세요.
          </p>
        </div>

        {loading ? (
          <p className="ledger-subtitle" style={{ textAlign: "center" }}>
            불러오는 중…
          </p>
        ) : visiblePayments.length === 0 && savings.length === 0 ? (
          <div className="ledger-center-y">
            <div className="ledger-title-block">
              <p className="ledger-subtitle">이 날짜에 저장된 내역이 없어요.</p>
            </div>
          </div>
        ) : (
          <>
            <TotalCard
              user={user}
              income={incomes.reduce((s, x) => s + x.amount, 0)}
              expense={expenses.reduce((s, x) => s + x.amount, 0)}
              savings={savingTotal}
            />

            {visiblePayments.map((pm) => (
              <PaymentGroup
                key={pm}
                label={PAYMENT_LABELS[pm]}
                income={paymentGroups[pm].income}
                expense={paymentGroups[pm].expense}
                items={paymentGroups[pm].items}
                open={isOpen(pm)}
                onToggle={() => toggleOpen(pm)}
              />
            ))}

            {savings.length > 0 && (
              <SavingGroup
                items={savings}
                total={savingTotal}
                open={isOpen("savings")}
                onToggle={() => toggleOpen("savings")}
              />
            )}
          </>
        )}

        <div className="ledger-actions">
          <div className="ledger-actions-row">
            <button type="button" className="ledger-back" onClick={goBack}>
              돌아가기
            </button>
            <button
              type="button"
              className="ledger-next"
              onClick={() => navigate("/home")}
            >
              완료하기
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}

// 그 날의 수입/지출/저축 총합. 토글·내역 없음.
// 좌측 동그라미는 사용자 아바타 (닉네임/이메일의 첫 글자).
function TotalCard({ user, income, expense, savings }) {
  const initial =
    (user?.nickname || user?.email || "?").trim().charAt(0).toUpperCase();
  return (
    <section className="ledger-pay-group ledger-pay-summary">
      <div className="ledger-pay-head">
        <span className="ledger-pay-icon ledger-pay-avatar" aria-hidden>
          {initial}
        </span>
        <span className="ledger-pay-label">전체</span>
        <div className="ledger-pay-totals">
          <div className="ledger-pay-income">
            {income > 0 ? `+ ${income.toLocaleString("ko-KR")} 원` : "0 원"}
          </div>
          <div className="ledger-pay-expense">
            {expense > 0 ? `- ${expense.toLocaleString("ko-KR")} 원` : "0 원"}
          </div>
        </div>
      </div>
      {savings > 0 && (
        <>
          <div className="ledger-pay-divider" />
          <div className="ledger-pay-items">
            <div className="ledger-pay-item">
              <div className="ledger-pay-item-left">
                <div className="ledger-pay-item-name">저축</div>
              </div>
              <div className="ledger-pay-item-amount">
                {savings.toLocaleString("ko-KR")} 원
              </div>
            </div>
          </div>
        </>
      )}
    </section>
  );
}

function PaymentGroup({ label, income, expense, items, open, onToggle }) {
  return (
    <section className="ledger-pay-group">
      <div className="ledger-pay-head">
        <span className="ledger-pay-icon" aria-hidden>●</span>
        <span className="ledger-pay-label">{label}</span>
        <div className="ledger-pay-totals">
          <div className="ledger-pay-income">
            {income > 0 ? `+ ${income.toLocaleString("ko-KR")} 원` : "0 원"}
          </div>
          <div className="ledger-pay-expense">
            {expense > 0 ? `- ${expense.toLocaleString("ko-KR")} 원` : "0 원"}
          </div>
        </div>
      </div>

      {items.length > 0 && <div className="ledger-pay-divider" />}

      {open && items.length > 0 && (
        <div className="ledger-pay-items">
          {items.map((it) => (
            <div key={it.id} className="ledger-pay-item">
              <div className="ledger-pay-item-left">
                <div className="ledger-pay-item-name">{it.name}</div>
                <div className="ledger-pay-item-cat">{it.category}</div>
              </div>
              <div
                className="ledger-pay-item-amount"
                style={{ color: it.sign === "+" ? "#4a5536" : "#c45a5a" }}
              >
                {it.sign} {it.amount.toLocaleString("ko-KR")} 원
              </div>
            </div>
          ))}
        </div>
      )}

      <button
        type="button"
        className="ledger-pay-toggle"
        onClick={onToggle}
        aria-label={open ? "접기" : "펼치기"}
      >
        {open ? "∧" : "∨"}
      </button>
    </section>
  );
}

function SavingGroup({ items, total, open, onToggle }) {
  return (
    <section className="ledger-pay-group">
      <div className="ledger-pay-head">
        <span className="ledger-pay-icon" aria-hidden>●</span>
        <span className="ledger-pay-label">저축</span>
        <div className="ledger-pay-totals">
          <div className="ledger-pay-income">
            {total > 0 ? `${total.toLocaleString("ko-KR")} 원` : "0 원"}
          </div>
        </div>
      </div>

      {items.length > 0 && <div className="ledger-pay-divider" />}

      {open && items.length > 0 && (
        <div className="ledger-pay-items">
          {items.map((s) => (
            <div key={s.id} className="ledger-pay-item">
              <div className="ledger-pay-item-left">
                <div className="ledger-pay-item-name">{s.item}</div>
                <div className="ledger-pay-item-cat">
                  {SAVING_TYPE_LABELS[s.savingType] || "저축"}
                </div>
              </div>
              <div className="ledger-pay-item-amount">
                {s.amount.toLocaleString("ko-KR")} 원
              </div>
            </div>
          ))}
        </div>
      )}

      <button
        type="button"
        className="ledger-pay-toggle"
        onClick={onToggle}
        aria-label={open ? "접기" : "펼치기"}
      >
        {open ? "∧" : "∨"}
      </button>
    </section>
  );
}

export default WalletConfirmPage;
