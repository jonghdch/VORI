import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import AppRightSidebar from "../../components/AppRightSidebar";
import { getHomeSummary } from "../../api/home";
import "./HomeDashboard.css";

// 스탯 4종 표시 메타 (값은 백엔드 stats 에서).
const STAT_META = [
  { key: "energy", label: "에너지", color: "var(--home-bar-green)" },
  { key: "charm", label: "매력", color: "var(--home-bar-red)" },
  { key: "iq", label: "지능", color: "var(--home-bar-orange)" },
  { key: "endurance", label: "지구력", color: "var(--home-bar-blue)" },
];

// 카테고리 막대 색 순환.
const CAT_COLORS = [
  "var(--home-bar-green)",
  "var(--home-bar-red)",
  "var(--home-bar-orange)",
  "var(--home-bar-blue)",
];

// 업적은 아직 백엔드 도메인 미연동 — 정적 유지.
const ACHIEVEMENTS = [
  { title: "첫 지출 기록", status: "완료", done: true },
  { title: "한 주 예산 지키기", status: "진행중", done: false },
  { title: "카페 지출 줄이기", status: "진행중", done: false },
  { title: "업적 10개 달성", status: "완료", done: true },
];

const won = (n) => `${(n ?? 0).toLocaleString("ko-KR")}원`;
const SIGNAL_ICON = { RED: "🔴", GRAY: "⚪", GREEN: "🟢" };

function HomeDashboard({ user, onNavigate, onLogout }) {
  const navigate = useNavigate();
  const nickname = user?.nickname || "사용자";

  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    getHomeSummary()
      .then((res) => {
        if (alive) setSummary(res);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const today = new Date();
  const dateStr = new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "long",
  }).format(today);

  const hour = today.getHours();
  const greeting =
    hour < 11 ? "좋은 아침이에요!" : hour < 18 ? "좋은 오후예요!" : "좋은 저녁이에요!";

  const stats = summary?.stats;
  const spending = summary?.spending;
  const recent = summary?.recentExpenses ?? [];
  const breakdown = summary?.categoryBreakdown ?? [];
  const monthTotal = breakdown.reduce((s, c) => s + c.amount, 0);

  return (
    <AppShell
      activeTop="home"
      activeSide="home"
      onNavigate={onNavigate}
      onLogout={onLogout}
    >
      <main className="home-main">
        <div className="home-main-header">
          <h1 className="home-greeting">
            {greeting} {nickname}님, 오늘도 보리와 함께해요!
          </h1>
          <p className="home-date">{dateStr}</p>
        </div>

        <div className="home-row home-row-pet">
          <section className="home-card home-card-pet">
            <p className="home-pet-stage">청소년기 · 성체까지 00일 남음</p>
            <div className="home-pet-head">
              <div>
                <h2 className="home-pet-name">보리</h2>
                <p className="home-pet-status">상태 : 배고픔</p>
              </div>
              <div className="home-pet-art" aria-hidden>
                <span className="home-pet-pixel">🐕</span>
              </div>
            </div>
            <div className="home-pet-bubble">
              어제 카페를 두 번이나 갔군요! 오늘은 아메리카노 한 잔만 해요
            </div>
          </section>

          <section className="home-card home-card-stats">
            <h2 className="home-card-title">스탯 현황</h2>
            <ul className="home-stat-list">
              {STAT_META.map((m) => {
                const value = stats?.[m.key] ?? 0;
                return (
                  <li key={m.key} className="home-stat-row">
                    <span className="home-stat-label">{m.label}</span>
                    <div className="home-stat-track">
                      <div
                        className="home-stat-fill"
                        style={{
                          width: `${Math.min(Math.max(value, 0), 100)}%`,
                          background: m.color,
                        }}
                      />
                    </div>
                    <span className="home-stat-num">{value}</span>
                  </li>
                );
              })}
            </ul>
          </section>
        </div>

        <div className="home-row home-row-kpi">
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">오늘 지출</h3>
            <p className="home-kpi-value">{won(spending?.today)}</p>
            <p className="home-kpi-sub">오늘 기록 {recent.length}건 기준</p>
          </article>
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">이번 달 누적</h3>
            <p className="home-kpi-value">{won(spending?.thisMonth)}</p>
            <p className="home-kpi-sub">이번 달 총 지출</p>
          </article>
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">이번 주 지출</h3>
            <p className="home-kpi-value">{won(spending?.thisWeek)}</p>
            <p className="home-kpi-sub">월요일부터 누적</p>
          </article>
        </div>

        <div className="home-row home-row-bottom">
          <section className="home-card home-card-list">
            <h2 className="home-card-title home-card-title--sm">최근 지출 내역</h2>
            <p className="home-list-date">최근 {recent.length}건</p>
            <ul className="home-tx-list">
              {loading ? (
                <li className="home-tx-row">불러오는 중…</li>
              ) : recent.length === 0 ? (
                <li className="home-tx-row">아직 지출 기록이 없어요.</li>
              ) : (
                recent.map((row) => (
                  <li key={row.id} className="home-tx-row">
                    <span className="home-tx-icon">
                      {SIGNAL_ICON[row.signalFinal] || "⚪"}
                    </span>
                    <div className="home-tx-mid">
                      <span className="home-tx-name">{row.item}</span>
                      <span className="home-tx-cat">{row.categoryName}</span>
                    </div>
                    <span className="home-tx-amount">{won(row.amount)}</span>
                  </li>
                ))
              )}
            </ul>
            <div className="home-card-actions">
              <button
                type="button"
                className="home-link-btn"
                onClick={() => navigate("/wallet")}
              >
                ▶ 상세 내역 확인하기
              </button>
              <button
                type="button"
                className="home-btn home-btn-dark"
                onClick={() => navigate("/wallet/new")}
              >
                + 지출 추가하기
              </button>
            </div>
          </section>

          <section className="home-card home-card-achieve">
            <h2 className="home-card-title home-card-title--sm">최근 업적</h2>
            <ul className="home-ach-list">
              {ACHIEVEMENTS.map((a) => (
                <li key={a.title} className="home-ach-row">
                  <span className="home-ach-title">{a.title}</span>
                  <span
                    className={`home-badge ${a.done ? "home-badge--done" : "home-badge--prog"}`}
                  >
                    {a.status}
                  </span>
                </li>
              ))}
            </ul>
            <button type="button" className="home-btn home-btn-primary home-btn-block">
              ▶ 더 많은 업적 확인하기
            </button>
          </section>

          <section className="home-card home-card-chart">
            <h2 className="home-card-title home-card-title--sm">카테고리별 지출</h2>
            <div className="home-cat-chart">
              {loading ? (
                <p className="home-cat-empty">불러오는 중…</p>
              ) : breakdown.length === 0 ? (
                <p className="home-cat-empty">이번 달 지출이 없어요.</p>
              ) : (
                breakdown.map((c, i) => (
                  <div key={c.categoryName} className="home-cat-row">
                    <span className="home-cat-label">{c.categoryName}</span>
                    <div className="home-cat-track">
                      <div
                        className="home-cat-fill"
                        style={{
                          width: monthTotal > 0 ? `${(c.amount / monthTotal) * 100}%` : "0%",
                          background: CAT_COLORS[i % CAT_COLORS.length],
                        }}
                      />
                    </div>
                    <span className="home-cat-amount">{won(c.amount)}</span>
                  </div>
                ))
              )}
            </div>
            <button
              type="button"
              className="home-btn home-btn-primary home-btn-block"
              onClick={() => navigate("/wallet")}
            >
              ▶ 리포트 확인하기
            </button>
          </section>
        </div>

        <p className="home-footnote">매일 오후 8시에 보리가 소비 검사를 시작해요</p>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default HomeDashboard;
