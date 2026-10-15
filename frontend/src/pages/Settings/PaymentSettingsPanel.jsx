import { useEffect, useState } from "react";
import { archiveInstrument, createInstrument, listInstruments, updateInstrument } from "../../api/paymentInstruments";
import IssuerBadge from "../../components/IssuerBadge";
import { INSTRUMENT_KINDS, ISSUERS, issuerOf } from "../../constants/issuers";

// 환경설정 > 카드·계좌. 등록해 두면 지출·수입·저축을 적을 때 결제 수단 칩으로 고를 수 있고,
// 가계부 목록 필터와 리포트의 결제수단별 지출에 쓰인다.
// 자동 연동이 아니라 "이런 카드를 쓴다"는 꼬리표. 카드 번호 같은 민감 정보는 받지 않는다.
const KIND_ORDER = ["DEBIT", "CREDIT", "CHECKING", "SAVINGS", "CASH"];
// 서버 검증(PaymentInstrumentCreateRequest @Max)과 같은 잔액 상한 — 넘으면 저장 전에 알려 준다
const MAX_BALANCE = 2_000_000_000;
const balanceTooBig = (v) => Number(v) > MAX_BALANCE;
const BALANCE_TOO_BIG = `잔액은 ${MAX_BALANCE.toLocaleString("ko-KR")}원까지 적을 수 있어요.`;

function PaymentSettingsPanel() {
  const [items, setItems] = useState(null); // null = 불러오는 중
  // 오류는 일어난 자리에 — 등록 칸(error) / 등록한 목록의 수정·삭제·불러오기(listError)
  const [error, setError] = useState("");
  const [listError, setListError] = useState("");
  const [saving, setSaving] = useState(false);
  // 고치는 중인 행 — { id, issuer, name, openingBalance }. null = 고치는 행 없음.
  const [editing, setEditing] = useState(null);
  const [kind, setKind] = useState("DEBIT");
  const [issuer, setIssuer] = useState(ISSUERS[0].code);
  const [name, setName] = useState("");
  const [openingBalance, setOpeningBalance] = useState("");

  const reload = () =>
    listInstruments()
      .then(setItems)
      .catch((e) => {
        setItems([]);
        setListError(e.message || "카드·계좌를 불러오지 못했어요.");
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
    if (hasBalance && balanceTooBig(openingBalance)) {
      setError(BALANCE_TOO_BIG);
      return;
    }
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

  const startEdit = (item) => {
    setListError("");
    setEditing({ id: item.id, issuer: item.issuer, name: item.name, openingBalance: String(item.openingBalance ?? 0) });
  };
  const handleUpdate = async (e, item) => {
    e.preventDefault();
    if (saving) return;
    if (!editing.name.trim()) {
      setListError("이름을 입력해주세요.");
      return;
    }
    if (item.group !== "CARD" && balanceTooBig(editing.openingBalance)) {
      setListError(BALANCE_TOO_BIG);
      return;
    }
    setSaving(true);
    setListError("");
    try {
      await updateInstrument(item.id, {
        issuer: item.kind === "CASH" ? item.issuer : editing.issuer,
        name: editing.name.trim(),
        openingBalance: item.group === "CARD" ? 0 : Number(editing.openingBalance) || 0,
      });
      setEditing(null);
      await reload();
    } catch (err) {
      setListError(err.message || "수정하지 못했어요.");
    } finally {
      setSaving(false);
    }
  };

  const handleArchive = async (item) => {
    if (saving) return; // 연타로 삭제 요청이 두 번 가지 않게
    if (!window.confirm(`'${item.name}'을 삭제할까요? 이 카드로 적은 지난 내역은 그대로 남아요.`)) return;
    setSaving(true);
    setListError("");
    try {
      await archiveInstrument(item.id);
      await reload();
    } catch (err) {
      setListError(err.message || "삭제하지 못했어요.");
    } finally {
      setSaving(false);
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
                placeholder={isCash ? "현금" : `예: ${kind === "SAVINGS" ? "토스 파킹통장, 청약" : kind === "CHECKING" ? "생활통장" : "K-패스"}`}
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
                  max={MAX_BALANCE}
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
        {listError && <p className="settings-hint-error" role="alert">{listError}</p>}
        {items == null ? (
          <p className="settings-row-desc">불러오는 중…</p>
        ) : items.length === 0 ? (
          <p className="settings-row-desc">아직 등록한 카드·계좌가 없어요.</p>
        ) : (
          <ul className="pay-list">
            {items.map((item) =>
              editing?.id === item.id ? (
                <li key={item.id} className="pay-item pay-item--editing">
                  <form className="pay-edit" onSubmit={(e) => handleUpdate(e, item)}>
                    <IssuerBadge issuer={item.kind === "CASH" ? item.issuer : editing.issuer} />
                    <div className="pay-edit-fields">
                      {item.kind !== "CASH" && (
                        <select
                          className="settings-select"
                          aria-label={item.group === "CARD" ? "카드사" : "은행"}
                          value={editing.issuer}
                          onChange={(e) => setEditing((d) => ({ ...d, issuer: e.target.value }))}
                        >
                          {ISSUERS.map((i) => (
                            <option key={i.code} value={i.code}>{i.name}</option>
                          ))}
                        </select>
                      )}
                      <input
                        className="settings-input"
                        aria-label="이름"
                        value={editing.name}
                        maxLength={40}
                        autoFocus
                        onChange={(e) => setEditing((d) => ({ ...d, name: e.target.value }))}
                      />
                      {item.group !== "CARD" && (
                        <input
                          className="settings-input pay-edit-balance"
                          aria-label="잔액"
                          type="number"
                          min="0"
                          max={MAX_BALANCE}
                          value={editing.openingBalance}
                          onChange={(e) => setEditing((d) => ({ ...d, openingBalance: e.target.value }))}
                        />
                      )}
                    </div>
                    <div className="pay-edit-actions">
                      <button type="submit" className="settings-save-btn" disabled={saving}>
                        {saving ? "저장 중…" : "저장"}
                      </button>
                      <button type="button" className="pay-item-cancel" onClick={() => setEditing(null)}>
                        취소
                      </button>
                    </div>
                  </form>
                  <p className="pay-edit-note">종류({INSTRUMENT_KINDS[item.kind]?.label})는 바꿀 수 없어요. 바꾸려면 새로 등록해 주세요.</p>
                </li>
              ) : (
              <li key={item.id} className="pay-item">
                <IssuerBadge issuer={item.issuer} />
                <div className="pay-item-text">
                  <strong>{item.name}</strong>
                  <span>
                    {INSTRUMENT_KINDS[item.kind]?.label}
                    {item.group !== "CARD" && ` · 잔액 ${item.openingBalance.toLocaleString("ko-KR")}원`}
                  </span>
                </div>
                <button type="button" className="pay-item-edit" onClick={() => startEdit(item)}>
                  수정
                </button>
                <button type="button" className="pay-item-archive" disabled={saving} onClick={() => handleArchive(item)}>
                  삭제
                </button>
              </li>
              ),
            )}
          </ul>
        )}
      </section>
    </div>
  );
}

export default PaymentSettingsPanel;
