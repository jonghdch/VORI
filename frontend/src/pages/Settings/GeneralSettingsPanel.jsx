import { useEffect, useState } from "react";
import { addFixedExpense, deleteFixedExpense, getFixedExpenses, getSpendingPlan } from "../../api/spendingPlan";

// 환경설정 > 기본 설정 탭. 현재는 기본 결제수단 하나만 — 추후 다른 항목 추가.
// 값은 localStorage 에 저장 (디바이스/브라우저 한정. 백엔드 persisting 은 나중).
const STORAGE_KEY = "user-settings";

const PAYMENT_METHODS = [
  { value: "CASH", label: "현금" },
  { value: "DEBIT", label: "체크카드" },
  { value: "CREDIT", label: "신용카드" },
  { value: "TRANSFER", label: "계좌이체" },
  { value: "MOBILE_PAY", label: "모바일페이" },
];
const STAT_BUDGET_LABELS = {
  ENERGY: "식비 · 에너지",
  CHARM: "쇼핑·뷰티 · 매력",
  IQ: "문화·여가 · 지능",
  ENDURANCE: "생활·고정비 · 지구력",
};

export function loadUserSettings() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    return JSON.parse(raw) || {};
  } catch {
    return {};
  }
}

function saveUserSettings(patch) {
  const cur = loadUserSettings();
  const next = { ...cur, ...patch };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {}
  return next;
}

function GeneralSettingsPanel() {
  const initial = loadUserSettings().defaultPaymentMethod || "CREDIT";
  const [defaultPayment, setDefaultPayment] = useState(initial);
  const [savedValue, setSavedValue] = useState(initial);
  const [savedNote, setSavedNote] = useState("");
  const [fixedExpenses, setFixedExpenses] = useState([]);
  const [fixedName, setFixedName] = useState("");
  const [fixedAmount, setFixedAmount] = useState("");
  const [plan, setPlan] = useState(null);
  const [fixedError, setFixedError] = useState("");

  const loadFixed = () => Promise.all([getFixedExpenses(), getSpendingPlan()]).then(([list, nextPlan]) => { setFixedExpenses(list || []); setPlan(nextPlan); });
  useEffect(() => { loadFixed().catch(() => setFixedError("고정비를 불러오지 못했어요.")); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!savedNote) return;
    const t = setTimeout(() => setSavedNote(""), 1500);
    return () => clearTimeout(t);
  }, [savedNote]);

  const hasChanges = defaultPayment !== savedValue;
  const handleSave = () => {
    if (!hasChanges) return;
    saveUserSettings({ defaultPaymentMethod: defaultPayment });
    setSavedValue(defaultPayment);
    setSavedNote("저장됨");
  };
  const addFixed = async (e) => {
    e.preventDefault();
    if (!fixedName.trim() || !Number(fixedAmount)) return;
    try { setFixedError(""); await addFixedExpense(fixedName.trim(), Number(fixedAmount)); setFixedName(""); setFixedAmount(""); await loadFixed(); }
    catch (err) { setFixedError(err.message); }
  };
  const removeFixed = async (id) => { try { await deleteFixedExpense(id); await loadFixed(); } catch (err) { setFixedError(err.message); } };

  return (
    <div className="settings-general">
      <section className="settings-section">
        <div className="settings-row">
          <div className="settings-row-label">
            <label className="settings-row-name" htmlFor="settings-default-payment">
              기본 결제수단
            </label>
            <div className="settings-row-desc">
              가계부에 새 항목을 추가할 때 기본으로 선택될 결제수단
            </div>
          </div>
          <div className="settings-row-control">
            <select
              id="settings-default-payment"
              className="settings-select"
              value={defaultPayment}
              onChange={(e) => setDefaultPayment(e.target.value)}
            >
              {PAYMENT_METHODS.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </section>

      <section className="settings-section">
        <div className="settings-row settings-fixed-head">
          <div className="settings-row-label"><h3 className="settings-row-name">고정비</h3><div className="settings-row-desc">통신비·보험·월세처럼 매달 나가는 금액이에요. 새 고정비는 다음 달 자동 예산에 반영되고, 이번 달 예산과 이미 받은 판정은 바뀌지 않아요.</div></div>
        </div>
        {plan && <p className="settings-row-desc">현재 등록된 고정비 {Number(plan.fixedTotal || 0).toLocaleString("ko-KR")}원 · 미확인 고정비 보류금 {Number(plan.reserveAmount || 0).toLocaleString("ko-KR")}원</p>}
        {plan && <div className="settings-budget-plan">
          <strong>{plan.yearMonth} 스탯별 월 예산</strong>
          {Object.entries(STAT_BUDGET_LABELS).map(([statType, label]) => (
            <span key={statType}>{label}<b>{Number(plan.budgets?.[statType] || 0).toLocaleString("ko-KR")}원</b></span>
          ))}
        </div>}
        <form className="settings-fixed-form" onSubmit={addFixed}>
          <input className="settings-input" value={fixedName} onChange={(e) => setFixedName(e.target.value)} placeholder="예: 휴대폰 통신비" maxLength="50" />
          <input className="settings-input" type="number" min="0" value={fixedAmount} onChange={(e) => setFixedAmount(e.target.value)} placeholder="월 금액" />
          <button type="submit" className="settings-save-btn">추가</button>
        </form>
        {fixedError && <p className="settings-hint-error">{fixedError}</p>}
        <div className="settings-fixed-list">{fixedExpenses.length === 0 ? <p className="settings-row-desc">등록한 고정비가 없어요.</p> : fixedExpenses.map((item) => <div className="settings-fixed-item" key={item.id}><span>{item.name}</span><strong>{Number(item.amount).toLocaleString("ko-KR")}원</strong><button type="button" onClick={() => removeFixed(item.id)}>삭제</button></div>)}</div>
      </section>

      <div className="settings-save-bar">
        {savedNote && <span className="settings-saved" role="status">{savedNote}</span>}
        <button
          type="button"
          className="settings-save-btn"
          onClick={handleSave}
          disabled={!hasChanges}
        >
          저장하기
        </button>
      </div>
    </div>
  );
}

export default GeneralSettingsPanel;
