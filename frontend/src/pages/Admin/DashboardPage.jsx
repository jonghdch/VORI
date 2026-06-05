import { useCallback, useEffect, useState } from "react";
import { getDashboardSummary } from "../../api/admin";
import RefreshButton from "./RefreshButton";
import "./DashboardPage.css";

function formatNumber(n) {
  if (n == null) return "—";
  return n.toLocaleString("ko-KR");
}

// KPI 카드 정의. accessor 로 summary 에서 값을 뽑고, suffix 는 단위.
const KPI_CARDS = [
  { key: "totalUsers", label: "전체 회원", suffix: "명" },
  { key: "newUsersToday", label: "오늘 신규 가입", suffix: "명" },
  { key: "adminCount", label: "관리자 계정", suffix: "명" },
  { key: "totalSaved", label: "누적 절약액 합계", suffix: "원" },
  { key: "totalExpenses", label: "지출 기록", suffix: "건" },
  { key: "totalAiInquiries", label: "AI 분석 호출", suffix: "회" },
];

// "yyyy-MM-dd" → "M/D" (차트 x축 라벨용)
function shortDate(iso) {
  const parts = iso.split("-");
  if (parts.length !== 3) return iso;
  return `${Number(parts[1])}/${Number(parts[2])}`;
}

function SignupTrendChart({ trend }) {
  const max = Math.max(1, ...trend.map((d) => d.count));
  return (
    <div className="admin-dash-chart">
      {trend.map((d) => {
        const heightPct = (d.count / max) * 100;
        return (
          <div className="admin-dash-bar-col" key={d.date}>
            <div className="admin-dash-bar-value">{d.count}</div>
            <div className="admin-dash-bar-track">
              <div
                className="admin-dash-bar-fill"
                style={{ height: `${heightPct}%` }}
                title={`${d.date}: ${d.count}명`}
              />
            </div>
            <div className="admin-dash-bar-label">{shortDate(d.date)}</div>
          </div>
        );
      })}
    </div>
  );
}

function DashboardPage() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    getDashboardSummary()
      .then((res) => setSummary(res))
      .catch((e) => setError(e.message || "불러오기 실패"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="admin-dash">
      <div className="admin-page-head">
        <div>
          <h1 className="admin-dash-title">종합 대시보드</h1>
          <p className="admin-dash-sub">VORI 운영 현황 한눈에 보기</p>
        </div>
        <RefreshButton onClick={load} disabled={loading} />
      </div>

      {error ? (
        <div className="admin-dash-state admin-dash-state--error">
          <span>{error}</span>
          <button type="button" onClick={load}>
            다시 시도
          </button>
        </div>
      ) : loading ? (
        <div className="admin-dash-state">불러오는 중…</div>
      ) : (
        <>
          <div className="admin-dash-kpis">
            {KPI_CARDS.map((card) => (
              <div
                key={card.key}
                className={
                  card.accent
                    ? "admin-dash-kpi admin-dash-kpi--accent"
                    : "admin-dash-kpi"
                }
              >
                <div className="admin-dash-kpi-label">{card.label}</div>
                <div className="admin-dash-kpi-value">
                  {formatNumber(summary[card.key])}
                  <span className="admin-dash-kpi-suffix">{card.suffix}</span>
                </div>
              </div>
            ))}
          </div>

          <div className="admin-dash-card">
            <div className="admin-dash-card-head">
              <h2 className="admin-dash-card-title">최근 7일 가입 추이</h2>
            </div>
            <SignupTrendChart trend={summary.signupTrend ?? []} />
          </div>
        </>
      )}
    </div>
  );
}

export default DashboardPage;
