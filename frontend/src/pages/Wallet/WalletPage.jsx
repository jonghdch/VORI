import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import "../Home/HomeDashboard.css";
import "./WalletPage.css";

const SUMMARY_CARDS = [
  { title: "이번 달 지출", value: "348,000원", sub: "예산 내 유지 중" },
  { title: "예산 잔액", value: "50,000원", sub: "300,000원 중" },
  { title: "이번 달 수입", value: "1,200,000원", sub: "급여 + 용돈" },
  { title: "AI 판정 완료", value: "13건", sub: "미판정 3건 남음" },
];

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const EXPENSES = [
  {
    id: "expense-movie",
    day: 6,
    icon: "🎟️",
    name: "영화관",
    cat: "문화",
    amount: "15,000원",
    date: "2026. 04. 06",
    time: "오후 08:15",
    payment: "카드",
    memo: "주말 영화 관람",
    reason: "주말 여가 활동으로 계획했던 문화 지출이에요.",
    aiStatus: "중립",
    aiMessage: "문화 지출은 적정하지만 다음 주 예산을 함께 확인해보세요.",
  },
  {
    id: "expense-cafe",
    day: 11,
    icon: "☕",
    name: "스타벅스",
    cat: "식비",
    amount: "5,500원",
    date: "2026. 04. 11",
    time: "오후 03:10",
    payment: "카드",
    memo: "오후 회의 전 커피",
    reason: "회의 전 집중을 위해 커피를 구매했어요.",
    aiStatus: "중립",
    aiMessage: "단발성 지출이지만 이번 주 카페 지출이 조금 늘었어요.",
  },
  {
    id: "expense-netflix",
    day: 12,
    icon: "🎬",
    name: "넷플릭스",
    cat: "고정비",
    amount: "28,000원",
    date: "2026. 04. 12",
    time: "오전 09:20",
    payment: "카드",
    memo: "월 정기 구독 결제",
    reason: "매달 사용하는 영상 구독 서비스라 고정 지출로 분류했어요.",
    aiStatus: "합리적",
    aiMessage: "정기 결제 패턴과 예산 범위 안에 있어 안정적인 소비로 보여요.",
  },
  {
    id: "expense-lunch",
    day: 12,
    icon: "🍱",
    name: "점심식사",
    cat: "식비",
    amount: "12,000원",
    date: "2026. 04. 12",
    time: "오후 12:35",
    payment: "체크카드",
    memo: "회사 근처 점심",
    reason: "업무 중 필요한 식사였고 하루 식비 예산 안에서 사용했어요.",
    aiStatus: "합리적",
    aiMessage: "평균 점심 지출과 비슷해 과소비 신호는 낮아요.",
  },
  {
    id: "expense-daiso",
    day: 27,
    icon: "🛒",
    name: "다이소",
    cat: "쇼핑",
    amount: "5,000원",
    date: "2026. 04. 27",
    time: "오후 06:40",
    payment: "체크카드",
    memo: "생활용품 구매",
    reason: "필요한 소모품을 한 번에 구매했어요.",
    aiStatus: "합리적",
    aiMessage: "생활 필수품 구매로 예산 범위 안에 있어요.",
  },
];

const INCOME_EVENTS = {
  10: [{ text: "월급 1,000,000", type: "income" }],
};

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

const AI_PENDING_COUNT = 3;
const CALENDAR_YEAR = 2026;
const CALENDAR_MONTH_INDEX = 3;
const CALENDAR_MONTH_LABEL = "04";
const CALENDAR_DAYS_IN_MONTH = new Date(
  CALENDAR_YEAR,
  CALENDAR_MONTH_INDEX + 1,
  0,
).getDate();

function buildApril2026Cells() {
  const firstDay = new Date(CALENDAR_YEAR, CALENDAR_MONTH_INDEX, 1).getDay();
  return [
    ...Array.from({ length: firstDay }, () => null),
    ...Array.from({ length: CALENDAR_DAYS_IN_MONTH }, (_, index) => index + 1),
  ];
}

function parseWon(amount) {
  return Number(amount.replace(/[^\d]/g, ""));
}

function formatWon(value) {
  return `${value.toLocaleString("ko-KR")}원`;
}

function getDayLabel(day) {
  const weekday = new Intl.DateTimeFormat("ko-KR", { weekday: "short" }).format(
    new Date(CALENDAR_YEAR, CALENDAR_MONTH_INDEX, day),
  );
  return `4월 ${day}일 (${weekday})`;
}

function getDateDisplay(day) {
  return `${CALENDAR_YEAR}. ${CALENDAR_MONTH_LABEL}. ${String(day).padStart(2, "0")}`;
}

function getExpensesByDay(day) {
  return EXPENSES.filter((expense) => expense.day === day);
}

function getCalendarEvents(day) {
  const expenseEvents = getExpensesByDay(day).map((expense) => ({
    text: `${expense.name} ${expense.amount.replace("원", "")}`,
    type: "expense",
  }));
  return [...(INCOME_EVENTS[day] || []), ...expenseEvents];
}

const CALENDAR_CELLS = buildApril2026Cells();

function WalletPage({ user, onLogout }) {
  const navigate = useNavigate();
  const nickname = user?.nickname || "사용자";
  const [selectedDay, setSelectedDay] = useState(12);
  const [selectedExpense, setSelectedExpense] = useState(() => getExpensesByDay(12)[0]);
  const [showExpenseHistory, setShowExpenseHistory] = useState(false);
  const [isAiActive, setIsAiActive] = useState(() => new Date().getHours() >= 20);

  useEffect(() => {
    const id = setInterval(() => {
      setIsAiActive(new Date().getHours() >= 20);
    }, 60_000);
    return () => clearInterval(id);
  }, []);

  const selectedDayExpenses = getExpensesByDay(selectedDay);
  const selectedDayTotal = selectedDayExpenses.reduce(
    (sum, expense) => sum + parseWon(expense.amount),
    0,
  );
  const historyTotal = EXPENSES.reduce((sum, expense) => sum + parseWon(expense.amount), 0);

  const selectDay = (day) => {
    const dayExpenses = getExpensesByDay(day);
    setSelectedDay(day);
    setSelectedExpense(dayExpenses[0] ?? null);
  };

  const moveSelectedDay = (direction) => {
    const nextDay = Math.min(
      CALENDAR_DAYS_IN_MONTH,
      Math.max(1, selectedDay + direction),
    );
    selectDay(nextDay);
  };

  const selectExpense = (expense) => {
    setSelectedDay(expense.day);
    setSelectedExpense(expense);
  };

  return (
    <AppShell
      activeTop="wallet"
      activeSide="wallet"
      user={user}
      onLogout={onLogout}
    >
      <main className="home-main ledger-main">
        <div className="ledger-header">
          <h1 className="ledger-greeting">
            {nickname}님, 오늘도 보리와 함께해요!
          </h1>
          <div className="ledger-header-actions">
            <div className="ledger-date-nav" aria-label="날짜 선택">
              <button
                type="button"
                className="ledger-date-arrow"
                aria-label="이전 날짜"
                disabled={selectedDay === 1}
                onClick={() => moveSelectedDay(-1)}
              >
                ‹
              </button>
              <span className="ledger-date-display">{getDateDisplay(selectedDay)}</span>
              <button
                type="button"
                className="ledger-date-arrow"
                aria-label="다음 날짜"
                disabled={selectedDay === CALENDAR_DAYS_IN_MONTH}
                onClick={() => moveSelectedDay(1)}
              >
                ›
              </button>
            </div>
            <button
              type="button"
              className={`home-btn ledger-history-toggle ${
                showExpenseHistory ? "is-active" : ""
              }`}
              aria-expanded={showExpenseHistory}
              onClick={() => setShowExpenseHistory((v) => !v)}
            >
              지출 내역
            </button>
            <button
              type="button"
              className="home-btn home-btn-primary ledger-add-btn"
              onClick={() => navigate("/wallet/new")}
            >
              + 지출 추가
            </button>
          </div>
        </div>

        {showExpenseHistory && (
          <section className="home-card ledger-history-card">
            <div className="ledger-history-head">
              <div>
                <h2 className="home-card-title home-card-title--sm">
                  전체 지출 내역
                </h2>
                <p className="ledger-history-sub">
                  이번 달 등록된 지출 {EXPENSES.length}건을 한 번에 확인해요.
                </p>
              </div>
              <span className="ledger-history-total">총 {formatWon(historyTotal)}</span>
            </div>

            <div className="ledger-history-table-wrap">
              <table className="ledger-history-table">
                <thead>
                  <tr>
                    <th>날짜</th>
                    <th>내역</th>
                    <th>카테고리</th>
                    <th>결제수단</th>
                    <th>AI 판정</th>
                    <th>금액</th>
                  </tr>
                </thead>
                <tbody>
                  {EXPENSES.map((expense) => (
                    <tr
                      key={expense.id}
                      role="button"
                      tabIndex={0}
                      className={selectedExpense?.id === expense.id ? "is-selected" : ""}
                      onClick={() => selectExpense(expense)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          selectExpense(expense);
                        }
                      }}
                    >
                      <td>
                        <span className="ledger-history-date">{expense.date}</span>
                        <span className="ledger-history-time">{expense.time}</span>
                      </td>
                      <td>
                        <span className="ledger-history-name">
                          {expense.icon} {expense.name}
                        </span>
                        <span className="ledger-history-memo">{expense.memo}</span>
                      </td>
                      <td>{expense.cat}</td>
                      <td>{expense.payment}</td>
                      <td>
                        <span className="ledger-history-badge">{expense.aiStatus}</span>
                      </td>
                      <td className="ledger-history-amount">{expense.amount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        <div className="ledger-row ledger-row-calendar">
          <section className="home-card ledger-calendar-card">
            <div className="ledger-cal-weekdays">
              {WEEKDAYS.map((weekday) => (
                <span key={weekday} className="ledger-cal-weekday">
                  {weekday}
                </span>
              ))}
            </div>
            <div className="ledger-cal-grid">
              {CALENDAR_CELLS.map((day, index) => {
                if (day === null) {
                  return (
                    <div
                      key={`empty-${index}`}
                      className="ledger-cal-cell ledger-cal-cell--empty"
                      aria-hidden
                    />
                  );
                }

                const events = getCalendarEvents(day);
                const isToday = day === 12;
                return (
                  <button
                    key={day}
                    type="button"
                    className={[
                      "ledger-cal-cell",
                      events.length > 0 ? "ledger-cal-cell--highlight" : "",
                      isToday ? "ledger-cal-cell--today" : "",
                      selectedDay === day ? "ledger-cal-cell--selected" : "",
                    ]
                      .filter(Boolean)
                      .join(" ")}
                    aria-pressed={selectedDay === day}
                    onClick={() => selectDay(day)}
                  >
                    <span className="ledger-cal-day">{day}</span>
                    <div className="ledger-cal-events">
                      {events.map((event) => (
                        <span
                          key={`${day}-${event.text}`}
                          className={`ledger-cal-event ledger-cal-event--${event.type}`}
                        >
                          {event.text}
                        </span>
                      ))}
                    </div>
                  </button>
                );
              })}
            </div>
          </section>

          <section className="home-card ledger-day-card">
            <div className="ledger-day-head">
              <h2 className="home-card-title home-card-title--sm">
                {getDayLabel(selectedDay)}
              </h2>
              <span className="ledger-day-total">{formatWon(selectedDayTotal)}</span>
            </div>

            <ul className="home-tx-list">
              {selectedDayExpenses.map((row) => (
                <li key={row.id} className="ledger-detail-list-item">
                  <button
                    type="button"
                    className={`home-tx-row ledger-detail-row ${
                      selectedExpense?.id === row.id ? "is-selected" : ""
                    }`}
                    onClick={() => setSelectedExpense(row)}
                  >
                    <span className="home-tx-icon">{row.icon}</span>
                    <div className="home-tx-mid">
                      <span className="home-tx-name">{row.name}</span>
                      <span className="home-tx-cat">{row.cat}</span>
                    </div>
                    <span className="home-tx-amount">{row.amount}</span>
                  </button>
                </li>
              ))}
            </ul>

            {selectedDayExpenses.length === 0 && (
              <div className="ledger-day-empty">
                <p>이 날짜에는 등록된 지출 내역이 없어요.</p>
              </div>
            )}

            {selectedExpense && (
              <div className="ledger-expense-detail" aria-live="polite">
                <div className="ledger-expense-detail-head">
                  <div>
                    <p className="ledger-detail-eyebrow">상세 내역</p>
                    <h3 className="ledger-detail-title">
                      {selectedExpense.icon} {selectedExpense.name}
                    </h3>
                  </div>
                  <span className="ledger-detail-amount">{selectedExpense.amount}</span>
                </div>

                <dl className="ledger-detail-grid">
                  <div>
                    <dt>날짜</dt>
                    <dd>{selectedExpense.date}</dd>
                  </div>
                  <div>
                    <dt>시간</dt>
                    <dd>{selectedExpense.time}</dd>
                  </div>
                  <div>
                    <dt>카테고리</dt>
                    <dd>{selectedExpense.cat}</dd>
                  </div>
                  <div>
                    <dt>결제수단</dt>
                    <dd>{selectedExpense.payment}</dd>
                  </div>
                </dl>

                <div className="ledger-detail-note">
                  <span>메모</span>
                  <p>{selectedExpense.memo}</p>
                </div>
                <div className="ledger-detail-note">
                  <span>소비 사유</span>
                  <p>{selectedExpense.reason}</p>
                </div>
                <div className="ledger-detail-ai">
                  <span className="ledger-detail-ai-badge">
                    AI 판정 · {selectedExpense.aiStatus}
                  </span>
                  <p>{selectedExpense.aiMessage}</p>
                </div>
              </div>
            )}
          </section>
        </div>

        <div className="ledger-row ledger-row-bottom">
          <section className="home-card ledger-report-card">
            <h2 className="home-card-title home-card-title--sm">보이는 리포트</h2>
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
                  <span className="ledger-legend-dot" style={{ background: item.color }} />
                  {item.label} {item.pct}%
                </li>
              ))}
            </ul>
          </section>

          <section className="home-card ledger-ai-card">
            <h2 className="home-card-title home-card-title--sm">AI 소비 판정</h2>
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
                {isAiActive ? "판정 시작하기" : "대기 중 (오후 8시 활성화)"}
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
                {CATEGORY_BARS.map((category) => (
                  <div key={category.label} className="home-cat-row">
                    <span className="home-cat-label">{category.label}</span>
                    <div className="home-cat-track">
                      <div
                        className="home-cat-fill"
                        style={{ width: `${category.pct}%`, background: category.color }}
                      />
                    </div>
                    <span className="home-cat-amount">{category.amount}</span>
                  </div>
                ))}
              </div>
            </section>

            <section className="home-card ledger-budget-card">
              <div className="ledger-budget-head">
                <h2 className="home-card-title home-card-title--sm">예산 현황</h2>
                <span className="ledger-budget-meta">잔액 52,000원 · 87% 사용</span>
              </div>
              <p className="ledger-budget-label">이번 달 지출 / 예산</p>
              <p className="ledger-budget-values">248,000원 / 300,000원</p>
              <div className="ledger-budget-track">
                <div className="ledger-budget-fill" style={{ width: "87%" }} />
              </div>
            </section>
          </div>
        </div>

        <div className="ledger-row ledger-row-summary ledger-row-summary--bottom">
          {SUMMARY_CARDS.map((card) => (
            <article key={card.title} className="home-card ledger-summary-card">
              <h3 className="home-kpi-title">{card.title}</h3>
              <p className="home-kpi-value">{card.value}</p>
              <p className="home-kpi-sub">{card.sub}</p>
            </article>
          ))}
        </div>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default WalletPage;
