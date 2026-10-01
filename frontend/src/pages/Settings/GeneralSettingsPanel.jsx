import { useEffect, useState } from "react";

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
