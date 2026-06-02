import { useCallback, useEffect, useState } from "react";
import { getRationalityRules, updateRationalityRules } from "../../api/admin";
import "./AdminCommon.css";
import "./RationalityRulesPage.css";

function RationalityRulesPage() {
  const [zRed, setZRed] = useState("");
  const [zGreen, setZGreen] = useState("");
  const [updatedAt, setUpdatedAt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getRationalityRules()
      .then((r) => {
        setZRed(String(r.zRed));
        setZGreen(String(r.zGreen));
        setUpdatedAt(r.updatedAt);
      })
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const red = Number(zRed);
  const green = Number(zGreen);
  const invalid =
    zRed === "" || zGreen === "" || Number.isNaN(red) || Number.isNaN(green) || green >= red;

  const save = () => {
    if (invalid) return;
    setSaving(true);
    setError(null);
    setNotice(null);
    updateRationalityRules({ zRed: red, zGreen: green })
      .then((r) => {
        setZRed(String(r.zRed));
        setZGreen(String(r.zGreen));
        setUpdatedAt(r.updatedAt);
        setNotice("저장되었습니다. 이후 작성되는 지출부터 새 기준이 적용됩니다.");
      })
      .catch((e) => setError(e.message || "저장 실패"))
      .finally(() => setSaving(false));
  };

  return (
    <div className="adm-page">
      <h1 className="adm-title">합리성 판정 / AI 룰 설정</h1>
      <p className="adm-sub">
        지출의 z-score(평소 대비 이례도)를 신호등으로 판정하는 임계값입니다. 저장하면 이후 작성되는
        지출부터 적용됩니다.
      </p>

      {error && <div className="rule-banner rule-banner--error">{error}</div>}
      {notice && <div className="rule-banner rule-banner--ok">{notice}</div>}

      {loading ? (
        <div className="adm-card">
          <div className="adm-state">불러오는 중…</div>
        </div>
      ) : (
        <div className="rule-grid">
          <section className="adm-card rule-card">
            <h2 className="rule-card-title">임계값</h2>

            <label className="rule-field">
              <span className="rule-field-label">
                🟢 GREEN 기준 (z ≤ z_green) — <code>z_green</code>
              </span>
              <input
                type="number"
                step="0.1"
                className="rule-input"
                value={zGreen}
                onChange={(e) => setZGreen(e.target.value)}
              />
            </label>

            <label className="rule-field">
              <span className="rule-field-label">
                🔴 RED 기준 (z &gt; z_red) — <code>z_red</code>
              </span>
              <input
                type="number"
                step="0.1"
                className="rule-input"
                value={zRed}
                onChange={(e) => setZRed(e.target.value)}
              />
            </label>

            {!invalid ? null : (
              <p className="rule-hint rule-hint--warn">
                z_green 은 z_red 보다 작아야 합니다.
              </p>
            )}

            <button
              type="button"
              className="rule-save"
              onClick={save}
              disabled={invalid || saving}
            >
              {saving ? "저장 중…" : "저장"}
            </button>
            {updatedAt && (
              <p className="rule-hint">최근 수정: {updatedAt.replace("T", " ").slice(0, 16)}</p>
            )}
          </section>

          <section className="adm-card rule-card">
            <h2 className="rule-card-title">판정 규칙</h2>
            <ul className="rule-explain">
              <li>
                <span className="adm-badge adm-badge--green">GREEN</span> z-score ≤{" "}
                <b>{zGreen || "z_green"}</b> — 평소보다 적게 쓴 절약 지출
              </li>
              <li>
                <span className="adm-badge adm-badge--gray">GRAY</span>{" "}
                <b>{zGreen || "z_green"}</b> &lt; z-score ≤ <b>{zRed || "z_red"}</b> — 평소·애매
              </li>
              <li>
                <span className="adm-badge adm-badge--red">RED</span> z-score &gt;{" "}
                <b>{zRed || "z_red"}</b> — 이례적 과지출 (AI 사유 질문 트리거)
              </li>
            </ul>
            <p className="rule-note">
              z-score = (지출액 − 평소 평균) / 평소 표준편차. 표본이 적거나(5건 미만) 반복 결제는
              판정에서 보호됩니다. 보정 매핑(reason_category)은 별도 로직으로 추후 노출 예정입니다.
            </p>
          </section>
        </div>
      )}
    </div>
  );
}

export default RationalityRulesPage;
