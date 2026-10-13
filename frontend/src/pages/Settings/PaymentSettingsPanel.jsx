import { useEffect, useState } from "react";
import { archiveInstrument, createInstrument, listInstruments } from "../../api/paymentInstruments";
import IssuerBadge from "../../components/IssuerBadge";
import { INSTRUMENT_KINDS, ISSUERS, issuerOf } from "../../constants/issuers";

// 환경설정 > 카드·계좌. 내가 쓰는 카드·계좌를 등록해 두면 가계부 왼쪽 자산 목록에 나온다.
// 자동 연동이 아니라 "이런 카드를 쓴다"는 꼬리표. 카드 번호 같은 민감 정보는 받지 않는다.
const KIND_ORDER = ["DEBIT", "CREDIT", "CHECKING", "SAVINGS", "CASH"];

function PaymentSettingsPanel() {
  const [items, setItems] = useState(null); // null = 불러오는 중
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [kind, setKind] = useState("DEBIT");
  const [issuer, setIssuer] = useState(ISSUERS[0].code);
  const [name, setName] = useState("");
  const [openingBalance, setOpeningBalance] = useState("");

  const reload = () =>
    listInstruments()
      .then(setItems)
      .catch((e) => {
        setItems([]);
        setError(e.message || "카드·계좌를 불러오지 못했어요.");
      });
  useEffect(() => { reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const isCash = kind === "CASH";
  const hasBalance = kind === "CHECKING" || kind === "SAVINGS" || isCash;

  const handleAdd = async (e) => {
    e.preventDefault();
    if (saving) return;
    const issuerName = isCash ? "" : issuerOf(issuer).name;
    // 이름을 비우면 "토스뱅크 체크카드"처럼 카드사 + 종류로 채운다.
    const finalName = name.trim() || `${issuerName} ${INSTRUMENT_KINDS[kind].label}`.trim();
    setSaving(true);
    setError("");
    try {
      await createInstrument({
        kind,
        issuer: isCash ? "ETC" : issuer,
        name: finalName,
        openingBalance: hasBalance ? Number(openingBalance) || 0 : 0,
      });
      setName("");
      setOpeningBalance("");
      await reload();
    } catch (err) {
      setError(err.message || "등록하지 못했어요.");
    } finally {
      setSaving(false);
    }
  };

  const handleArchive = async (item) => {
    if (!window.confirm(`'${item.name}'을 삭제할까요? 이 카드로 적은 지난 내역은 그대로 남아요.`)) return;
    setError("");
    try {
      await archiveInstrument(item.id);
      await reload();
    } catch (err) {
      setError(err.message || "삭제하지 못했어요.");
    }
  };

  return (
    <div className="settings-general">
      <section className="settings-section">
        <div className="settings-row-label">
          <h3 className="settings-row-name">새 카드·계좌 등록</h3>
          <div className="settings-row-desc">
            어떤 카드·계좌를 쓰는지만 적어 두면 돼요. 카드 번호는 받지 않아요.
          </div>
        </div>
        <form className="pay-form" onSubmit={handleAdd}>
          <div className="pay-kind" role="radiogroup" aria-label="종류">
            {KIND_ORDER.map((k) => (
              <button
                key={k}
                type="button"
                role="radio"
                aria-checked={kind === k}
                className={`pay-kind-btn ${kind === k ? "is-active" : ""}`}
                onClick={() => setKind(k)}
              >
                {INSTRUMENT_KINDS[k].label}
              </button>
            ))}
          </div>
          <div className="pay-fields">
            {!isCash && (
              <label className="pay-field">
                <span>{kind === "DEBIT" || kind === "CREDIT" ? "카드사" : "은행"}</span>
                <select className="settings-select" value={issuer} onChange={(e) => setIssuer(e.target.value)}>
                  {ISSUERS.map((i) => (
                    <option key={i.code} value={i.code}>{i.name}</option>
                  ))}
                </select>
              </label>
            )}
            <label className="pay-field pay-field--grow">
              <span>이름</span>
              <input
                className="settings-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={isCash ? "현금" : `예: ${kind === "SAVINGS" ? "청년도약계좌" : "K-패스"}`}
                maxLength={40}
              />
            </label>
            {hasBalance && (
              <label className="pay-field">
                <span>지금 잔액</span>
                <input
                  className="settings-input"
                  type="number"
                  min="0"
                  value={openingBalance}
                  onChange={(e) => setOpeningBalance(e.target.value)}
                  placeholder="0"
                />
              </label>
            )}
            <button type="submit" className="settings-save-btn pay-add-btn" disabled={saving}>
              {saving ? "등록 중…" : "등록"}
            </button>
          </div>
        </form>
        {error && <p className="settings-hint-error" role="alert">{error}</p>}
      </section>

      <section className="settings-section">
        <h3 className="settings-row-name">등록한 카드·계좌</h3>
        {items == null ? (
          <p className="settings-row-desc">불러오는 중…</p>
        ) : items.length === 0 ? (
          <p className="settings-row-desc">아직 등록한 카드·계좌가 없어요.</p>
        ) : (
          <ul className="pay-list">
            {items.map((item) => (
              <li key={item.id} className="pay-item">
                <IssuerBadge issuer={item.issuer} />
                <div className="pay-item-text">
                  <strong>{item.name}</strong>
                  <span>
                    {INSTRUMENT_KINDS[item.kind]?.label}
                    {item.group !== "CARD" && ` · 잔액 ${item.openingBalance.toLocaleString("ko-KR")}원`}
                  </span>
                </div>
                <button type="button" className="pay-item-archive" onClick={() => handleArchive(item)}>
                  삭제
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

export default PaymentSettingsPanel;
