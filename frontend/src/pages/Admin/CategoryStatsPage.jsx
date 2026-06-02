import { useCallback, useEffect, useState } from "react";
import { getCategoryStats } from "../../api/admin";
import "./AdminCommon.css";

const won = (n) => (n == null ? "—" : `${n.toLocaleString("ko-KR")}원`);
const num = (n) => (n == null ? "—" : n.toLocaleString("ko-KR"));

function SignalBar({ red, gray, green }) {
  const total = red + gray + green;
  if (total === 0) return <span className="adm-cell-sub">—</span>;
  const pct = (v) => `${(v / total) * 100}%`;
  return (
    <div className="adm-signal-bar" title={`🔴${red} ⚪${gray} 🟢${green}`}>
      <span className="adm-signal-seg--red" style={{ width: pct(red) }} />
      <span className="adm-signal-seg--gray" style={{ width: pct(gray) }} />
      <span className="adm-signal-seg--green" style={{ width: pct(green) }} />
    </div>
  );
}

function CategoryStatsPage() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getCategoryStats()
      .then((res) => setRows(res))
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const grandTotal = rows.reduce((s, r) => s + r.totalAmount, 0);

  return (
    <div className="adm-page">
      <h1 className="adm-title">지출 카테고리 통계</h1>
      <p className="adm-sub">
        전체 사용자 기준 카테고리별 지출 집계입니다.{" "}
        {!loading && !error && rows.length > 0 && (
          <span className="adm-accent">총 {won(grandTotal)}</span>
        )}
      </p>

      <div className="adm-toolbar">
        <span />
        <button type="button" className="adm-btn" onClick={load} disabled={loading}>
          새로고침
        </button>
      </div>

      <div className="adm-card">
        {error ? (
          <div className="adm-state adm-state--error">
            <span>{error}</span>
            <button type="button" onClick={load}>
              다시 시도
            </button>
          </div>
        ) : loading ? (
          <div className="adm-state">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="adm-state">집계할 지출 데이터가 없습니다.</div>
        ) : (
          <div className="adm-table-wrap">
            <table className="adm-table">
              <thead>
                <tr>
                  <th>카테고리</th>
                  <th className="num">건수</th>
                  <th className="num">총 지출액</th>
                  <th className="num">평균</th>
                  <th>신호 분포 (🔴⚪🟢)</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.categoryId}>
                    <td>{r.categoryName}</td>
                    <td className="num">{num(r.count)}</td>
                    <td className="num">{won(r.totalAmount)}</td>
                    <td className="num">{won(r.avgAmount)}</td>
                    <td>
                      <SignalBar red={r.redCount} gray={r.grayCount} green={r.greenCount} />
                      <span className="adm-cell-sub">
                        🔴 {r.redCount} · ⚪ {r.grayCount} · 🟢 {r.greenCount}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

export default CategoryStatsPage;
