import AppShell from "../../components/AppShell";
import AppRightSidebar from "../../components/AppRightSidebar";
import "../Home/HomeDashboard.css";
import "./LedgerPage.css";

const SUMMARY_CARDS = [
  {
    title: "이번 달 지출",
    value: "348,000원",
    sub: "예산 내 유지 중",
    warn: false,
  },
  {
    title: "예산 잔액",
    value: "50,000원",
    sub: "300,000원 중",
    warn: false,
  },
  {
    title: "이번 달 수입",
    value: "1,200,000원",
    sub: "급여 + 용돈",
    warn: false,
  },
  {
    title: "AI 판정 완료",
    value: "13건",
    sub: "미판정 3건 남음",
    warn: false,
  },
];

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const CALENDAR_EVENTS = {
  10: [{ text: "월급 1,000,000", type: "income" }],
  12: [
    { text: "넷플릭스 28,000", type: "expense" },
    { text: "식비 12,000", type: "expense" },
  ],
  27: [{ text: "다이소 5,000", type: "expense" }],
};

const HIGHLIGHT_DAYS = [12, 27];

const DAY_DETAIL = [
  {
    icon: "🎬",
    name: "넷플릭스",
    cat: "고정비",
    amount: "28,000원",
  },
  {
    icon: "🍱",
    name: "점심식사",
    cat: "식비",
    amount: "12,000원",
  },
];

const CATEGORY_BARS = [
  { label: "식비", pct: 25, amount: "10,000", color: "var(--home-bar-green)" },
  { label: "쇼핑", pct: 26, amount: "10,000", color: "var(--home-bar-red)" },
  { label: "문화", pct: 25, amount: "10,000", color: "var(--home-bar-orange)" },
  { label: "고정비", pct: 25, amount: "10,000", color: "var(--home-bar-blue)" },
];

const DONUT_LEGEND = [
  { label: "식비", pct: 25, color: "var(--home-bar-green)" },
  { label: "쇼핑", pct: 26, color: "var(--home-bar-red)" },
  { label: "문화", pct: 25, color: "var(--home-bar-orange)" },
  { label: "고정비", pct: 25, color: "var(--home-bar-blue)" },
];

function buildApril2026Cells() {
  const year = 2026;
  const month = 3;
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells = [];
  for (let i = 0; i < firstDay; i += 1) cells.push(null);
  for (let d = 1; d <= daysInMonth; d += 1) cells.push(d);
  return cells;
}

const CALENDAR_CELLS = buildApril2026Cells();

const AI_PENDING_COUNT = 3;

function LedgerPage({ user, onNavigate, onLogout }) {
  const nickname = user?.nickname || "사용자";
  const isAiActive = new Date().getHours() >= 20;

  return (
    <AppShell
      activeTop="ledger"
      activeSide="ledger"
      onNavigate={onNavigate}
      onLogout={onLogout}
    >
      <main className="home-main ledger-main">
        <div className="ledger-header">
          <h1 className="ledger-greeting">
            {nickname}님, 오늘도 보리와 함께해요!
          </h1>
          <div className="ledger-header-actions">
            <div className="ledger-date-nav" aria-label="월 선택">
              <button type="button" className="ledger-date-arrow" aria-label="이전">
                ‹
              </button>
              <span className="ledger-date-display">2026. 04. 01</span>
              <button type="button" className="ledger-date-arrow" aria-label="다음">
                ›
              </button>
            </div>
            <button type="button" className="home-btn home-btn-primary ledger-add-btn">
              + 지출 추가
            </button>
          </div>
        </div>

        <div className="ledger-row ledger-row-summary">
          {SUMMARY_CARDS.map((card) => (
            <article key={card.title} className="home-card ledger-summary-card">
              <h3 className="home-kpi-title">{card.title}</h3>
              <p className="home-kpi-value">{card.value}</p>
              <p className="home-kpi-sub">{card.sub}</p>
            </article>
          ))}
        </div>

        <div className="ledger-row ledger-row-calendar">
          <section className="home-card ledger-calendar-card">
            <div className="ledger-cal-weekdays">
              {WEEKDAYS.map((w) => (
                <span key={w} className="ledger-cal-weekday">
                  {w}
                </span>
              ))}
            </div>
            <div className="ledger-cal-grid">
              {CALENDAR_CELLS.map((day, idx) => {
                if (day === null) {
                  return (
                    <div
                      key={`empty-${idx}`}
                      className="ledger-cal-cell ledger-cal-cell--empty"
                      aria-hidden
                    />
                  );
                }
                const events = CALENDAR_EVENTS[day] || [];
                const isHighlight = HIGHLIGHT_DAYS.includes(day);
                const isToday = day === 12;
                return (
                  <div
                    key={day}
                    className={[
                      "ledger-cal-cell",
                      isHighlight ? "ledger-cal-cell--highlight" : "",
                      isToday ? "ledger-cal-cell--today" : "",
                    ]
                      .filter(Boolean)
                      .join(" ")}
                  >
                    <span className="ledger-cal-day">{day}</span>
                    <div className="ledger-cal-events">
                      {events.map((ev) => (
                        <span
                          key={ev.text}
                          className={`ledger-cal-event ledger-cal-event--${ev.type}`}
                        >
                          {ev.text}
                        </span>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          <section className="home-card ledger-day-card">
            <div className="ledger-day-head">
              <h2 className="home-card-title home-card-title--sm">
                4월 12일 (목)
              </h2>
              <span className="ledger-day-total">40,000원</span>
            </div>
            <ul className="home-tx-list">
              {DAY_DETAIL.map((row) => (
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
          </section>
        </div>

        <div className="ledger-row ledger-row-bottom">
          <section className="home-card ledger-report-card">
            <h2 className="home-card-title home-card-title--sm">
              보이는 리포트
            </h2>
            <div className="ledger-donut-wrap">
              <div
                className="ledger-donut"
                role="img"
                aria-label="이번 달 지출 248,000원, 예산 300,000원"
              >
                <div className="ledger-donut-hole">
                  <span className="ledger-donut-amount">248,000원</span>
                  <span className="ledger-donut-budget">/ 300,000원</span>
                </div>
              </div>
            </div>
            <ul className="ledger-donut-legend">
              {DONUT_LEGEND.map((item) => (
                <li key={item.label}>
                  <span
                    className="ledger-legend-dot"
                    style={{ background: item.color }}
                  />
                  {item.label} {item.pct}%
                </li>
              ))}
            </ul>
          </section>

          <section className="home-card ledger-ai-card">
            <h2 className="home-card-title home-card-title--sm">
              AI 소비 판정
            </h2>
            <div className="ledger-ai-box">
              <p className="ledger-ai-text">보리가 오늘 소비를 분석해요</p>
            </div>
            <div className="ledger-ai-actions">
              <button
                type="button"
                className={`home-btn ledger-ai-action-btn ${
                  isAiActive
                    ? "ledger-ai-action-btn--active"
                    : "ledger-ai-action-btn--waiting"
                }`}
                disabled={!isAiActive}
              >
                {isAiActive
                  ? "판정 시작하기"
                  : "대기 중 (오후 8시 활성화)"}
              </button>
              <p className="ledger-ai-footnote">
                오늘 미판정 제출 {AI_PENDING_COUNT}건
              </p>
            </div>
          </section>

          <div className="ledger-bottom-right">
            <section className="home-card ledger-cat-card">
              <h2 className="home-card-title home-card-title--sm">
                카테고리별 지출
              </h2>
              <div className="ledger-cat-chart">
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
            </section>

            <section className="home-card ledger-budget-card">
              <div className="ledger-budget-head">
                <h2 className="home-card-title home-card-title--sm">
                  예산 현황
                </h2>
                <span className="ledger-budget-meta">
                  잔액 52,000원 · 87% 사용
                </span>
              </div>
              <p className="ledger-budget-label">이번 달 지출 / 예산</p>
              <p className="ledger-budget-values">248,000원 / 300,000원</p>
              <div className="ledger-budget-track">
                <div className="ledger-budget-fill" style={{ width: "87%" }} />
              </div>
            </section>
          </div>
        </div>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default LedgerPage;
