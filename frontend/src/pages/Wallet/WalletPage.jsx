import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import { getMonthlyLedger } from "../../api/ledger";
import { listInquiriesByDate } from "../../api/inquiries";
import "../Home/HomeDashboard.css";
import "./WalletPage.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 카테고리 차트·도넛 공용 팔레트 (상위 카테고리 순서대로 순환).
const CHART_COLORS = [
  "var(--home-bar-green)",
  "var(--home-bar-red)",
  "var(--home-bar-orange)",
  "var(--home-bar-blue)",
];

// 합리성 시그널(백엔드 enum) → 한글 상태 + 배지 색상 클래스.
const SIGNAL_STATUS = { GREEN: "합리적", GRAY: "중립", RED: "비합리적" };

// 결제수단 enum(백엔드 PaymentMethod) → 한글 라벨.
const PAYMENT_LABEL = {
  CASH: "현금",
  DEBIT: "체크카드",
  CREDIT: "신용카드",
  TRANSFER: "계좌이체",
  MOBILE_PAY: "모바일페이",
};
const SIGNAL_BADGE = {
  GREEN: "ledger-history-badge--green",
  GRAY: "ledger-history-badge--gray",
  RED: "ledger-history-badge--red",
};

// 카테고리/타입 → 아이콘 (백엔드가 아이콘을 주지 않으므로 프론트에서 매핑).
const CATEGORY_ICON = {
  식비: "🍚",
  카페: "☕",
  문화: "🎟️",
  쇼핑: "🛍️",
  교통: "🚌",
  생활: "🧺",
  고정비: "🏠",
  의료: "💊",
  교육: "📚",
};

function iconFor(row) {
  if (row.type === "INCOME") return "💰";
  return CATEGORY_ICON[row.cat] || "💸";
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function formatWon(value) {
  return `${value.toLocaleString("ko-KR")}원`;
}

// "2026-06-09" → "2026. 06. 09"
function formatDateDisplay(isoDate) {
  const [y, m, d] = isoDate.split("-");
  return `${y}. ${m}. ${d}`;
}

function dayLabel(year, month, day) {
  const weekday = new Intl.DateTimeFormat("ko-KR", { weekday: "short" }).format(
    new Date(year, month - 1, day),
  );
  return `${month}월 ${day}일 (${weekday})`;
}

// 백엔드 LedgerResponse → 화면이 쓰는 행 형태로 변환.
function toRow(item) {
  const day = Number(item.date.split("-")[2]);
  return {
    id: `${item.type}-${item.id}`,
    type: item.type, // "EXPENSE" | "INCOME"
    day,
    name: item.item || (item.type === "INCOME" ? "수입" : "지출"),
    cat: item.category || "—",
    amountNum: item.amount,
    amount: formatWon(item.amount),
    date: formatDateDisplay(item.date),
    signal: item.signal, // GREEN | GRAY | RED | null
    // AI 판정은 예외적 지출(aiJudged)에만. 평소 지출은 배지 미표시.
    aiJudged: Boolean(item.aiJudged),
    aiStatus: item.aiJudged && item.signal ? SIGNAL_STATUS[item.signal] : null,
    payment: item.paymentMethod ? PAYMENT_LABEL[item.paymentMethod] : null,
    memo: item.memo || "",
    // AI 질문에 답한 소비 사유 — 답변한 예외 지출에만 존재.
    reason: item.reason || "",
  };
}

function WalletPage({ user, onLogout }) {
  const navigate = useNavigate();
  const nickname = user?.nickname || "사용자";

  // 보고 있는 달 (1-based month). 처음엔 실제 이번 달부터.
  const today = useMemo(() => new Date(), []);
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth() + 1);

  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [selectedDay, setSelectedDay] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [showExpenseHistory, setShowExpenseHistory] = useState(false);
  const [isAiActive, setIsAiActive] = useState(() => new Date().getHours() >= 20);

  useEffect(() => {
    const id = setInterval(() => {
      setIsAiActive(new Date().getHours() >= 20);
    }, 60_000);
    return () => clearInterval(id);
  }, []);

  // 달이 바뀌면 그 달 데이터를 다시 불러오고 선택 초기화.
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    setSelectedDay(null);
    setSelectedId(null);
    getMonthlyLedger(`${viewYear}-${pad2(viewMonth)}`)
      .then((data) => {
        if (!alive) return;
        setRows(Array.isArray(data) ? data.map(toRow) : []);
      })
      .catch((e) => {
        if (!alive) return;
        // 세션 만료 — 거짓 "빈 달" 대신 로그인으로 보냄
        if (e.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setError(e.message || "불러오기 실패");
        setRows([]);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [viewYear, viewMonth, navigate]);

  // 달력 셀 구성 (선행 빈칸 + 1일~말일).
  const daysInMonth = new Date(viewYear, viewMonth, 0).getDate();
  const firstWeekday = new Date(viewYear, viewMonth - 1, 1).getDay();
  const calendarCells = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  // 일자별 행 그룹.
  const rowsByDay = useMemo(() => {
    const map = new Map();
    rows.forEach((row) => {
      if (!map.has(row.day)) map.set(row.day, []);
      map.get(row.day).push(row);
    });
    return map;
  }, [rows]);

  const expenseRows = rows.filter((r) => r.type === "EXPENSE");
  const monthExpenseTotal = expenseRows.reduce((sum, r) => sum + r.amountNum, 0);
  const incomeRows = rows.filter((r) => r.type === "INCOME");
  const monthIncomeTotal = incomeRows.reduce((sum, r) => sum + r.amountNum, 0);
  // AI 질문을 거친 예외 지출 수 (이 달 기준).
  const aiJudgedCount = expenseRows.filter((r) => r.aiJudged).length;

  // 카테고리별 지출 집계 — 금액 내림차순 상위 4개 (차트·도넛 공용).
  const categoryBreakdown = useMemo(() => {
    const byCat = new Map();
    expenseRows.forEach((r) => {
      byCat.set(r.cat, (byCat.get(r.cat) || 0) + r.amountNum);
    });
    return [...byCat.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 4)
      .map(([label, amount], i) => ({
        label,
        amount,
        pct: monthExpenseTotal > 0 ? Math.round((amount / monthExpenseTotal) * 100) : 0,
        color: CHART_COLORS[i % CHART_COLORS.length],
      }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows]);

  // 도넛 = 상위 카테고리 비중 conic-gradient (CSS 고정값 대신 실데이터).
  const donutGradient = useMemo(() => {
    if (monthExpenseTotal <= 0) return null;
    let acc = 0;
    const stops = categoryBreakdown.map((c) => {
      const from = acc;
      acc += (c.amount / monthExpenseTotal) * 360;
      return `${c.color} ${from}deg ${acc}deg`;
    });
    // 상위 4개 밖 나머지 금액은 회색으로 채움.
    if (acc < 359.9) stops.push(`#e8e3d8 ${acc}deg 360deg`);
    return `conic-gradient(${stops.join(", ")})`;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryBreakdown, monthExpenseTotal]);

  // 오늘 미답변 AI 질문 수 — 실카운트. 실패 시 null 로 두고 문구 숨김.
  const [pendingCount, setPendingCount] = useState(null);
  useEffect(() => {
    let alive = true;
    const iso = `${today.getFullYear()}-${pad2(today.getMonth() + 1)}-${pad2(today.getDate())}`;
    listInquiriesByDate(iso)
      .then((data) => {
        if (alive) setPendingCount(Array.isArray(data) ? data.length : null);
      })
      .catch(() => {
        if (alive) setPendingCount(null);
      });
    return () => {
      alive = false;
    };
  }, [today]);

  const selectedDayRows = selectedDay ? rowsByDay.get(selectedDay) ?? [] : [];
  const selectedDayTotal = selectedDayRows
    .filter((r) => r.type === "EXPENSE")
    .reduce((sum, r) => sum + r.amountNum, 0);
  const selectedRow = selectedId
    ? rows.find((r) => r.id === selectedId) ?? null
    : null;

  // 보고 있는 달이 실제 이번 달일 때만 '오늘' 표시.
  const todayDay =
    viewYear === today.getFullYear() && viewMonth === today.getMonth() + 1
      ? today.getDate()
      : null;

  const changeMonth = (delta) => {
    let m = viewMonth + delta;
    let y = viewYear;
    if (m < 1) {
      m = 12;
      y -= 1;
    } else if (m > 12) {
      m = 1;
      y += 1;
    }
    setViewYear(y);
    setViewMonth(m);
  };

  const selectDay = (day) => {
    setSelectedDay(day);
    const dayRows = rowsByDay.get(day) ?? [];
    setSelectedId(dayRows[0]?.id ?? null);
  };

  const selectRow = (row) => {
    setSelectedDay(row.day);
    setSelectedId(row.id);
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
            <div className="ledger-date-nav" aria-label="달 선택">
              <button
                type="button"
                className="ledger-date-arrow"
                aria-label="이전 달"
                onClick={() => changeMonth(-1)}
              >
                ‹
              </button>
              <span className="ledger-date-display">
                {viewYear}. {pad2(viewMonth)}
              </span>
              <button
                type="button"
                className="ledger-date-arrow"
                aria-label="다음 달"
                onClick={() => changeMonth(1)}
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
                  {viewYear}년 {viewMonth}월 등록된 지출 {expenseRows.length}건을
                  한 번에 확인해요.
                </p>
              </div>
              <span className="ledger-history-total">
                총 {formatWon(monthExpenseTotal)}
              </span>
            </div>

            <div className="ledger-history-table-wrap">
              <table className="ledger-history-table">
                <thead>
                  <tr>
                    <th>날짜</th>
                    <th>내역</th>
                    <th>카테고리</th>
                    <th>AI 판정</th>
                    <th>금액</th>
                  </tr>
                </thead>
                <tbody>
                  {expenseRows.map((row) => (
                    <tr
                      key={row.id}
                      role="button"
                      tabIndex={0}
                      className={selectedId === row.id ? "is-selected" : ""}
                      onClick={() => selectRow(row)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          selectRow(row);
                        }
                      }}
                    >
                      <td>
                        <span className="ledger-history-date">{row.date}</span>
                      </td>
                      <td>
                        <span className="ledger-history-name">
                          {iconFor(row)} {row.name}
                        </span>
                      </td>
                      <td>{row.cat}</td>
                      <td>
                        {row.aiStatus ? (
                          <span
                            className={`ledger-history-badge ${
                              SIGNAL_BADGE[row.signal] ||
                              "ledger-history-badge--gray"
                            }`}
                          >
                            {row.aiStatus}
                          </span>
                        ) : (
                          // 예외 지출이 아니면 AI 판정 미표시
                          <span className="ledger-history-ai-none" aria-hidden>—</span>
                        )}
                      </td>
                      <td className="ledger-history-amount">{row.amount}</td>
                    </tr>
                  ))}
                  {!loading && expenseRows.length === 0 && (
                    <tr>
                      <td colSpan={5} className="ledger-history-empty-cell">
                        이 달에는 등록된 지출이 없어요.
                      </td>
                    </tr>
                  )}
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
              {calendarCells.map((day, index) => {
                if (day === null) {
                  return (
                    <div
                      key={`empty-${index}`}
                      className="ledger-cal-cell ledger-cal-cell--empty"
                      aria-hidden
                    />
                  );
                }

                const dayRows = rowsByDay.get(day) ?? [];
                const isToday = day === todayDay;
                return (
                  <button
                    key={day}
                    type="button"
                    className={[
                      "ledger-cal-cell",
                      dayRows.length > 0 ? "ledger-cal-cell--highlight" : "",
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
                      {dayRows.map((row) => (
                        <span
                          key={row.id}
                          className={`ledger-cal-event ledger-cal-event--${
                            row.type === "INCOME" ? "income" : "expense"
                          }`}
                        >
                          {row.name} {row.amountNum.toLocaleString("ko-KR")}
                        </span>
                      ))}
                    </div>
                  </button>
                );
              })}
            </div>
            {loading && (
              <p className="ledger-cal-state">불러오는 중…</p>
            )}
            {error && !loading && (
              <p className="ledger-cal-state ledger-cal-state--error">
                {error}
              </p>
            )}
          </section>

          <div className="ledger-day-col">
          <section className="home-card ledger-day-card">
            <div className="ledger-day-head">
              <h2 className="home-card-title home-card-title--sm">
                {selectedDay
                  ? dayLabel(viewYear, viewMonth, selectedDay)
                  : "날짜를 선택하세요"}
              </h2>
              {selectedDay && (
                <span className="ledger-day-total">
                  {formatWon(selectedDayTotal)}
                </span>
              )}
            </div>

            <div className="ledger-day-body">
            <ul className="home-tx-list">
              {selectedDayRows.map((row) => (
                <li key={row.id} className="ledger-detail-list-item">
                  <button
                    type="button"
                    className={`home-tx-row ledger-detail-row ${
                      selectedId === row.id ? "is-selected" : ""
                    }`}
                    onClick={() => setSelectedId(row.id)}
                  >
                    <span className="home-tx-icon">{iconFor(row)}</span>
                    <div className="home-tx-mid">
                      <span className="home-tx-name">{row.name}</span>
                      <span className="home-tx-cat">{row.cat}</span>
                    </div>
                    <span className="home-tx-amount">{row.amount}</span>
                  </button>
                </li>
              ))}
            </ul>

            {selectedDay && selectedDayRows.length === 0 && (
              <div className="ledger-day-empty">
                <p>이 날짜에는 등록된 내역이 없어요.</p>
              </div>
            )}

            {selectedRow && (
              <div className="ledger-expense-detail" aria-live="polite">
                <div className="ledger-expense-detail-head">
                  <p className="ledger-detail-eyebrow">상세 내역</p>
                  <div className="ledger-detail-title-row">
                    <span className="ledger-detail-icon" aria-hidden>
                      {iconFor(selectedRow)}
                    </span>
                    <div className="ledger-detail-title-amount">
                      <h3 className="ledger-detail-title">{selectedRow.name}</h3>
                      <span className="ledger-detail-amount">
                        {selectedRow.amount}
                      </span>
                    </div>
                  </div>
                </div>

                <dl className="ledger-detail-grid">
                  <div>
                    <dt>날짜</dt>
                    <dd>{selectedRow.date}</dd>
                  </div>
                  <div>
                    <dt>구분</dt>
                    <dd>
                      <span
                        className={`ledger-detail-type ${
                          selectedRow.type === "INCOME"
                            ? "ledger-detail-type--income"
                            : "ledger-detail-type--expense"
                        }`}
                      >
                        {selectedRow.type === "INCOME" ? "수입" : "지출"}
                      </span>
                    </dd>
                  </div>
                  <div>
                    <dt>카테고리</dt>
                    <dd>{selectedRow.cat}</dd>
                  </div>
                  <div>
                    <dt>결제수단</dt>
                    <dd>{selectedRow.payment || "—"}</dd>
                  </div>
                </dl>

                <div className="ledger-detail-note">
                  <span>메모</span>
                  <p>{selectedRow.memo}</p>
                </div>
                {selectedRow.reason && (
                  <div className="ledger-detail-note">
                    <span>소비 사유</span>
                    <p>{selectedRow.reason}</p>
                  </div>
                )}
                {selectedRow.aiStatus && (
                  <div className="ledger-detail-ai">
                    <span className="ledger-detail-ai-badge">
                      AI 판정 · {selectedRow.aiStatus}
                    </span>
                  </div>
                )}
              </div>
            )}
            </div>
          </section>
          </div>
        </div>

        <div className="ledger-row ledger-row-bottom">
          <section className="home-card ledger-report-card">
            <h2 className="home-card-title home-card-title--sm">보이는 리포트</h2>
            {donutGradient ? (
              <>
                <div className="ledger-donut-wrap">
                  <div
                    className="ledger-donut"
                    role="img"
                    aria-label={`이번 달 지출 ${formatWon(monthExpenseTotal)}`}
                    style={{ background: donutGradient }}
                  >
                    <div className="ledger-donut-hole">
                      <span className="ledger-donut-amount">
                        {formatWon(monthExpenseTotal)}
                      </span>
                      <span className="ledger-donut-budget">이번 달 지출</span>
                    </div>
                  </div>
                </div>
                <ul className="ledger-donut-legend">
                  {categoryBreakdown.map((item) => (
                    <li key={item.label}>
                      <span className="ledger-legend-dot" style={{ background: item.color }} />
                      {item.label} {item.pct}%
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <p className="ledger-card-empty">이번 달 지출이 없어요.</p>
            )}
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
                onClick={() => navigate("/wallet/analysis")}
              >
                {isAiActive ? "판정 시작하기" : "대기 중 (오후 8시 활성화)"}
              </button>
              {pendingCount != null && (
                <p className="ledger-ai-footnote">
                  오늘 미답변 질문 {pendingCount}건
                </p>
              )}
            </div>
          </section>

          <div className="ledger-bottom-right">
            <section className="home-card ledger-cat-card">
              <h2 className="home-card-title home-card-title--sm">
                카테고리별 지출
              </h2>
              {categoryBreakdown.length > 0 ? (
                <div className="ledger-cat-chart">
                  {categoryBreakdown.map((category) => (
                    <div key={category.label} className="home-cat-row">
                      <span className="home-cat-label">{category.label}</span>
                      <div className="home-cat-track">
                        <div
                          className="home-cat-fill"
                          style={{ width: `${category.pct}%`, background: category.color }}
                        />
                      </div>
                      <span className="home-cat-amount">
                        {category.amount.toLocaleString("ko-KR")}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="ledger-card-empty">이번 달 지출이 없어요.</p>
              )}
            </section>

            {/* 예산 API 미구현 — 가짜 수치 대신 준비 중임을 명시 */}
            <section className="home-card ledger-budget-card">
              <div className="ledger-budget-head">
                <h2 className="home-card-title home-card-title--sm">예산 현황</h2>
              </div>
              <p className="ledger-card-empty">예산 설정 기능을 준비 중이에요.</p>
            </section>
          </div>
        </div>

        <div className="ledger-row ledger-row-summary ledger-row-summary--bottom">
          {[
            {
              title: "이번 달 지출",
              value: formatWon(monthExpenseTotal),
              sub: `지출 ${expenseRows.length}건`,
            },
            {
              title: "이번 달 수입",
              value: formatWon(monthIncomeTotal),
              sub: `수입 ${incomeRows.length}건`,
            },
            {
              title: "AI 판정",
              value: `${aiJudgedCount}건`,
              sub:
                pendingCount != null
                  ? `오늘 미답변 ${pendingCount}건`
                  : "예외적인 지출만 판정해요",
            },
            {
              title: "이번 달 기록",
              value: `${rows.length}건`,
              sub: `지출 ${expenseRows.length} · 수입 ${incomeRows.length}`,
            },
          ].map((card) => (
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
