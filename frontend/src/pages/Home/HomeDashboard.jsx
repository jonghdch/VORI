import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import AppRightSidebar from "../../components/AppRightSidebar";
import "./HomeDashboard.css";

const STATS = [
  { key: "energy", label: "에너지", value: 68, color: "var(--home-bar-green)" },
  { key: "charm", label: "매력", value: 68, color: "var(--home-bar-red)" },
  { key: "int", label: "지능", value: 68, color: "var(--home-bar-orange)" },
  { key: "sta", label: "지구력", value: 68, color: "var(--home-bar-blue)" },
];

const TX_ROWS = [
  { icon: "🎬", name: "Netflix", cat: "고정비", amount: "17,000원" },
  { icon: "🍱", name: "점심 식사", cat: "식비", amount: "12,500원" },
  { icon: "☕", name: "스타벅스", cat: "식비", amount: "3,000원" },
];

const ACHIEVEMENTS = [
  { title: "첫 지출 기록", status: "완료", done: true },
  { title: "한 주 예산 지키기", status: "진행중", done: false },
  { title: "카페 지출 줄이기", status: "진행중", done: false },
  { title: "업적 10개 달성", status: "완료", done: true },
];

const CATEGORY_BARS = [
  { label: "식비", pct: 42, amount: "146,200원", color: "var(--home-bar-green)" },
  { label: "쇼핑", pct: 22, amount: "76,500원", color: "var(--home-bar-red)" },
  { label: "문화", pct: 18, amount: "62,600원", color: "var(--home-bar-orange)" },
  { label: "고정비", pct: 18, amount: "62,600원", color: "var(--home-bar-blue)" },
];

function HomeDashboard({ user, onNavigate, onLogout }) {
  const navigate = useNavigate();
  const nickname = user?.nickname || "사용자";

  const today = new Date();
  const dateStr = new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "long",
  }).format(today);

  const hour = today.getHours();
  const greeting =
    hour < 11
      ? "좋은 아침이에요!"
      : hour < 18
        ? "좋은 오후예요!"
        : "좋은 저녁이에요!";

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
              {STATS.map((s) => (
                <li key={s.key} className="home-stat-row">
                  <span className="home-stat-label">{s.label}</span>
                  <div className="home-stat-track">
                    <div
                      className="home-stat-fill"
                      style={{ width: `${s.value}%`, background: s.color }}
                    />
                  </div>
                  <span className="home-stat-num">{s.value}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>

        <div className="home-row home-row-kpi">
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">오늘 지출</h3>
            <p className="home-kpi-value">32,500원</p>
            <p className="home-kpi-sub home-kpi-sub--warn">
              목표 대비 +12% 초과
            </p>
          </article>
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">이번 달 누적</h3>
            <p className="home-kpi-value">348,000원</p>
            <p className="home-kpi-sub">예산 내 유지 중</p>
          </article>
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">이번 주 지출</h3>
            <p className="home-kpi-value">124,800원</p>
            <p className="home-kpi-sub home-kpi-sub--warn">
              지난 주 대비 +8,200원
            </p>
          </article>
        </div>

        <div className="home-row home-row-bottom">
          <section className="home-card home-card-list">
            <h2 className="home-card-title home-card-title--sm">
              최근 지출 내역
            </h2>
            <p className="home-list-date">5월 12일 (목)</p>
            <ul className="home-tx-list">
              {TX_ROWS.map((row) => (
                <li key={row.name} className="home-tx-row">
                  <span className="home-tx-icon">{row.icon}</span>
                  <div className="home-tx-mid">
                    <span className="home-tx-name">{row.name}</span>
                    <span className="home-tx-cat">{row.cat}</span>
                  </div>
                  <span className="home-tx-amount">{row.amount}</span>
                </li>
              ))}
            </ul>
            <div className="home-card-actions">
              <button type="button" className="home-link-btn">
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
            <h2 className="home-card-title home-card-title--sm">
              카테고리별 지출
            </h2>
            <div className="home-cat-chart">
              {CATEGORY_BARS.map((c) => (
                <div key={c.label} className="home-cat-row">
                  <span className="home-cat-label">{c.label}</span>
                  <div className="home-cat-track">
                    <div
                      className="home-cat-fill"
                      style={{ width: `${c.pct}%`, background: c.color }}
                    />
                  </div>
                  <span className="home-cat-amount">{c.amount}</span>
                </div>
              ))}
            </div>
            <button type="button" className="home-btn home-btn-primary home-btn-block">
              ▶ 리포트 확인하기
            </button>
          </section>
        </div>

        <p className="home-footnote">
          매일 오후 8시에 보리가 소비 검사를 시작해요
        </p>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default HomeDashboard;
