import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import RecordCalendar, { dateKey } from "../../components/RecordCalendar";
import { getMonthlyLedger } from "../../api/ledger";
import "../Wallet/WalletPage.css";
import "./ReportPage.css";

// 합리성 시그널(백엔드 enum) → 한글 상태 + 배지 색상 클래스.
const SIGNAL_STATUS = { GREEN: "합리적", GRAY: "중립", RED: "비합리적" };
const SIGNAL_BADGE = {
  GREEN: "ledger-history-badge--green",
  GRAY: "ledger-history-badge--gray",
  RED: "ledger-history-badge--red",
};

// 결제수단 enum(백엔드 PaymentMethod) → 한글 라벨.
const PAYMENT_LABEL = {
  CASH: "현금",
  DEBIT: "체크카드",
  CREDIT: "신용카드",
  TRANSFER: "계좌이체",
  MOBILE_PAY: "모바일페이",
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

// 백엔드 LedgerResponse → 화면이 쓰는 행 형태로 변환 (year/month 도 함께 보관).
function toRow(item) {
  const [y, m, d] = item.date.split("-").map(Number);
  return {
    id: `${item.type}-${item.id}`,
    type: item.type, // "EXPENSE" | "INCOME"
    year: y,
    month: m,
    day: d,
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

// 지출 행 → 카테고리별 합계 상위 N개.
function topCategories(expenseRows, n = 5) {
  const byCat = new Map();
  expenseRows.forEach((r) => byCat.set(r.cat, (byCat.get(r.cat) || 0) + r.amountNum));
  return [...byCat.entries()]
    .map(([cat, amount]) => ({ cat, amount }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, n);
}

const CAT_BAR_COLORS = [
  "var(--home-bar-green)",
  "var(--home-bar-orange)",
  "var(--home-bar-blue)",
  "var(--home-bar-red)",
  "var(--home-muted)",
];

// 소비 리포트 — 주/월 단위 대시보드. /wallet 보이는 리포트의 "자세히보기"가 이곳으로 온다.
function ReportPage({ user, onLogout }) {
  const navigate = useNavigate();

  const today = useMemo(() => new Date(), []);
  const todayKey = dateKey(today.getFullYear(), today.getMonth() + 1, today.getDate());

  // 보기 단위(주/월)와 기준일. 이동 시 anchor 만 갱신되고 기간이 따라간다.
  const [viewMode, setViewMode] = useState("week");
  const [anchor, setAnchor] = useState(() => new Date());

  // 보고 있는 기간의 날짜 목록 — 주: 일요일 시작 7일, 월: 1일~말일.
  const periodDates = useMemo(() => {
    if (viewMode === "month") {
      const y = anchor.getFullYear();
      const m = anchor.getMonth();
      const n = new Date(y, m + 1, 0).getDate();
      return Array.from({ length: n }, (_, i) => new Date(y, m, i + 1));
    }
    const startOffset = anchor.getDay(); // 0=일 … 6=토
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(anchor);
      d.setDate(d.getDate() - startOffset + i);
      return d;
    });
  }, [viewMode, anchor]);

  // 기간이 걸친 달(주 모드 최대 2개) — "YYYY-MM" 목록. 이 달들의 가계부를 받아온다.
  const yearMonths = useMemo(() => {
    const set = new Set();
    periodDates.forEach((d) => set.add(`${d.getFullYear()}-${pad2(d.getMonth() + 1)}`));
    return [...set];
  }, [periodDates]);

  // 기간 포함 판별용 키 집합.
  const periodKeySet = useMemo(
    () =>
      new Set(periodDates.map((d) => dateKey(d.getFullYear(), d.getMonth() + 1, d.getDate()))),
    [periodDates],
  );

  // 캘린더에 그릴 달 — 월 모드는 그 달, 주 모드는 7일 중 더 많은 날이 속한 달.
  const { calYear, calMonth } = useMemo(() => {
    if (viewMode === "month") {
      return { calYear: anchor.getFullYear(), calMonth: anchor.getMonth() + 1 };
    }
    const count = new Map();
    periodDates.forEach((d) => {
      const k = `${d.getFullYear()}-${d.getMonth() + 1}`;
      count.set(k, (count.get(k) || 0) + 1);
    });
    const [best] = [...count.entries()].sort((a, b) => b[1] - a[1])[0];
    const [y, m] = best.split("-").map(Number);
    return { calYear: y, calMonth: m };
  }, [viewMode, anchor, periodDates]);

  const [rows, setRows] = useState([]);
  // 캘린더용 — 기간 필터 전, 받아온 달 전체 행. null = 로딩 중.
  const [monthRows, setMonthRows] = useState(null);
  // 캘린더 셀 클릭으로 기간을 옮길 때, 다음 로딩에서 그 날짜를 기본 선택으로 삼는다.
  const pendingSelectRef = useRef(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  // 표 선택/미리보기 — {day, month, id}. 기본값: 오늘, 오늘 첫 행(없으면 id null).
  const [selected, setSelected] = useState(() => ({
    day: today.getDate(),
    month: today.getMonth() + 1,
    id: null,
  }));
  const [preview, setPreview] = useState(null);

  // 보고 있는 기간에 오늘이 포함되는지 — 기본 선택 규칙 분기용.
  const containsToday = periodKeySet.has(todayKey);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    Promise.all(yearMonths.map((ym) => getMonthlyLedger(ym)))
      .then((results) => {
        if (!alive) return;
        const merged = results.flat().map(toRow);
        const mapped = merged.filter((r) => periodKeySet.has(dateKey(r.year, r.month, r.day)));
        setMonthRows(merged);
        setRows(mapped);
        // 기본 선택: 캘린더에서 고른 날 > 기간 안의 오늘 > 기간 첫날.
        const pending = pendingSelectRef.current;
        pendingSelectRef.current = null;
        const pickFor = (day, month) =>
          mapped.filter((r) => r.day === day && r.month === month)[0]?.id ?? null;
        if (pending) {
          setSelected({ ...pending, id: pickFor(pending.day, pending.month) });
        } else if (containsToday) {
          const day = today.getDate();
          const month = today.getMonth() + 1;
          setSelected({ day, month, id: pickFor(day, month) });
        } else {
          const first = periodDates[0];
          const day = first.getDate();
          const month = first.getMonth() + 1;
          setSelected({ day, month, id: pickFor(day, month) });
        }
      })
      .catch((e) => {
        if (!alive) return;
        // 세션 만료 — 거짓 "빈 기간" 대신 로그인으로 보냄
        if (e.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setError(e.message || "불러오기 실패");
        setRows([]);
        setMonthRows([]);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [yearMonths, periodKeySet, periodDates, containsToday, navigate, today, reloadKey]);

  const expenseRows = rows.filter((r) => r.type === "EXPENSE");
  const incomeRows = rows.filter((r) => r.type === "INCOME");
  const weekExpenseTotal = expenseRows.reduce((sum, r) => sum + r.amountNum, 0);
  const incomeTotal = incomeRows.reduce((sum, r) => sum + r.amountNum, 0);

  // ── 기간 통계 ─────────────────────────────────────────
  // 기록한 날 수 / 기간 일수, 하루 평균(오늘까지 지난 날 기준 — 미래 날짜로 평균을 희석하지 않음).
  const recordedDayCount = new Set(rows.map((r) => dateKey(r.year, r.month, r.day))).size;
  const elapsedDays = Math.max(
    1,
    periodDates.filter((d) => d <= today).length,
  );
  const dailyAvg = Math.round(weekExpenseTotal / elapsedDays);

  // AI 판정(예외 지출) 분포.
  const judged = expenseRows.filter((r) => r.aiJudged && r.signal);
  const signalCount = { GREEN: 0, GRAY: 0, RED: 0 };
  judged.forEach((r) => {
    signalCount[r.signal] += 1;
  });
  const signalTotal = judged.length;

  const topCats = topCategories(expenseRows);
  const topCatMax = topCats[0]?.amount || 0;

  // 우측 카드 기준 — 호버 중엔 미리보기, 벗어나면 고정(selected)으로 복귀.
  const active = preview ?? selected;

  const activeDayRows = active
    ? rows.filter((r) => r.day === active.day && r.month === active.month)
    : [];
  const activeDayTotal = activeDayRows
    .filter((r) => r.type === "EXPENSE")
    .reduce((sum, r) => sum + r.amountNum, 0);
  const activeRow = active?.id ? rows.find((r) => r.id === active.id) ?? null : null;
  const activeYear = active
    ? periodDates.find((d) => d.getMonth() + 1 === active.month && d.getDate() === active.day)?.getFullYear() ??
      today.getFullYear()
    : today.getFullYear();

  const selectRow = (row) => {
    setSelected({ day: row.day, month: row.month, id: row.id });
  };

  // 기록 캘린더 — 기록한 날·보고 있는 기간 키 집합.
  const calRecordedKeys = useMemo(
    () =>
      monthRows === null
        ? null
        : new Set(monthRows.map((r) => dateKey(r.year, r.month, r.day))),
    [monthRows],
  );

  // 캘린더 셀 클릭 → 그 날이 속한 기간으로 이동(이미 그 기간이면 유지) + 그 날을 선택.
  const jumpToDay = (date) => {
    const day = date.getDate();
    const month = date.getMonth() + 1;
    const key = dateKey(date.getFullYear(), month, day);
    if (periodKeySet.has(key)) {
      setSelected({ day, month, id: rows.find((r) => r.day === day && r.month === month)?.id ?? null });
      return;
    }
    pendingSelectRef.current = { day, month };
    setAnchor(new Date(date));
  };

  const movePeriod = (delta) => {
    setAnchor((d) => {
      const n = new Date(d);
      if (viewMode === "month") {
        n.setDate(1);
        n.setMonth(n.getMonth() + delta);
      } else {
        n.setDate(n.getDate() + delta * 7);
      }
      return n;
    });
  };

  // 헤더 날짜 네비 표시 — 주: "MM. DD ~ MM. DD", 월: "YYYY. MM" (가계부와 동일 포맷).
  const periodLabel = useMemo(() => {
    if (viewMode === "month") {
      return `${anchor.getFullYear()}. ${pad2(anchor.getMonth() + 1)}`;
    }
    const first = periodDates[0];
    const last = periodDates[6];
    return `${pad2(first.getMonth() + 1)}. ${pad2(first.getDate())} ~ ${pad2(
      last.getMonth() + 1,
    )}. ${pad2(last.getDate())}`;
  }, [viewMode, anchor, periodDates]);

  const unitLabel = viewMode === "week" ? "주" : "달";

  return (
    <AppShell activeTop="wallet" activeSide="report" user={user} onLogout={onLogout}>
      <main className="home-main">
        <div className="ledger-header">
          <h1 className="ledger-greeting report-greeting">소비 리포트</h1>
          <div className="ledger-header-actions">
            <div className="ledger-view-toggle" role="group" aria-label="보기 방식">
              <button
                type="button"
                className={viewMode === "week" ? "is-active" : ""}
                aria-pressed={viewMode === "week"}
                onClick={() => setViewMode("week")}
              >
                주
              </button>
              <button
                type="button"
                className={viewMode === "month" ? "is-active" : ""}
                aria-pressed={viewMode === "month"}
                onClick={() => setViewMode("month")}
              >
                월
              </button>
            </div>
            <div className="ledger-date-nav" aria-label={`${unitLabel} 선택`}>
              <button
                type="button"
                className="ledger-date-arrow"
                aria-label={`이전 ${unitLabel}`}
                onClick={() => movePeriod(-1)}
              >
                ‹
              </button>
              <span className="ledger-date-display">{periodLabel}</span>
              <button
                type="button"
                className="ledger-date-arrow"
                aria-label={`다음 ${unitLabel}`}
                onClick={() => movePeriod(1)}
              >
                ›
              </button>
            </div>
          </div>
        </div>

        {/* ── 상단: 기록 캘린더 · 기간 요약 · 카테고리 TOP ── */}
        <div className="home-row report-top-row">
          <section className="home-card report-cal-card">
            <div className="ledger-history-head">
              <h2 className="home-card-title home-card-title--sm report-card-title">
                {periodLabel}
              </h2>
              <span className="report-cal-legend">
                <i className="report-cal-swatch is-marked" /> 기록
                <i className="report-cal-swatch is-highlight" /> 보는 {unitLabel}
              </span>
            </div>
            <RecordCalendar
              year={calYear}
              month={calMonth}
              recordedKeys={calRecordedKeys}
              highlightKeys={periodKeySet}
              showAdjacent={viewMode === "week"}
              onSelectDay={jumpToDay}
            />
          </section>

          <section className="home-card report-summary-card">
            <h2 className="home-card-title home-card-title--sm report-card-title">
              {viewMode === "week" ? "이번 주 요약" : "이번 달 요약"}
              <span className="report-card-period">{periodLabel}</span>
            </h2>
            <div className="report-kpi-grid">
              <div className="report-kpi">
                <p className="home-kpi-title">총 지출</p>
                <p className="home-kpi-value">{formatWon(weekExpenseTotal)}</p>
                <p className="home-kpi-sub">{expenseRows.length}건</p>
              </div>
              <div className="report-kpi">
                <p className="home-kpi-title">총 수입</p>
                <p className="home-kpi-value">{formatWon(incomeTotal)}</p>
                <p className="home-kpi-sub">{incomeRows.length}건</p>
              </div>
              <div className="report-kpi">
                <p className="home-kpi-title">기록한 날</p>
                <p className="home-kpi-value">
                  {recordedDayCount}
                  <span className="report-kpi-unit">/ {periodDates.length}일</span>
                </p>
                <p className="home-kpi-sub">
                  {periodDates.length > 0
                    ? `${Math.round((recordedDayCount / periodDates.length) * 100)}% 기록`
                    : "—"}
                </p>
              </div>
              <div className="report-kpi">
                <p className="home-kpi-title">하루 평균 지출</p>
                <p className="home-kpi-value">{formatWon(dailyAvg)}</p>
                <p className="home-kpi-sub">지난 {elapsedDays}일 기준</p>
              </div>
            </div>

            <div className="report-signal">
              <div className="report-signal-head">
                <span className="home-kpi-title">AI 판정 분포</span>
                <span className="home-kpi-sub">{signalTotal}건 판정</span>
              </div>
              {signalTotal === 0 ? (
                <p className="report-signal-empty">이 {unitLabel}에는 AI 판정을 거친 지출이 없어요.</p>
              ) : (
                <>
                  <div className="report-signal-bar" aria-hidden>
                    {["GREEN", "GRAY", "RED"].map((s) =>
                      signalCount[s] > 0 ? (
                        <span
                          key={s}
                          className={`report-signal-seg report-signal-seg--${s.toLowerCase()}`}
                          style={{ width: `${(signalCount[s] / signalTotal) * 100}%` }}
                        />
                      ) : null,
                    )}
                  </div>
                  <ul className="report-signal-legend">
                    {["GREEN", "GRAY", "RED"].map((s) => (
                      <li key={s}>
                        <i className={`report-signal-dot report-signal-seg--${s.toLowerCase()}`} />
                        {SIGNAL_STATUS[s]} {signalCount[s]}
                      </li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          </section>

          <section className="home-card report-cat-card">
            <h2 className="home-card-title home-card-title--sm report-card-title">
              카테고리 TOP 5
              <span className="report-card-period">{periodLabel}</span>
            </h2>
            {loading ? (
              <p className="report-signal-empty">불러오는 중…</p>
            ) : topCats.length === 0 ? (
              <p className="report-signal-empty">이 {unitLabel}에는 지출이 없어요.</p>
            ) : (
              <ul className="report-cat-list">
                {topCats.map((c, i) => (
                  <li key={c.cat} className="report-cat-row">
                    <span className="report-cat-label">{c.cat}</span>
                    <div className="home-stat-track report-cat-track">
                      <div
                        className="home-stat-fill"
                        style={{
                          width: topCatMax > 0 ? `${(c.amount / topCatMax) * 100}%` : "0%",
                          background: CAT_BAR_COLORS[i % CAT_BAR_COLORS.length],
                        }}
                      />
                    </div>
                    <span className="report-cat-amount">
                      {formatWon(c.amount)}
                      <small>
                        {weekExpenseTotal > 0
                          ? ` ${Math.round((c.amount / weekExpenseTotal) * 100)}%`
                          : ""}
                      </small>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <div className="ledger-row report-week-row">
          <section className="ledger-history-card report-week-table">
            <div className="ledger-history-head">
              <span className="ledger-history-chip">{viewMode === "week" ? "주간 지출 내역" : "월간 지출 내역"}</span>
              <span className="ledger-history-chip ledger-history-chip--total">
                총 {formatWon(weekExpenseTotal)}
              </span>
            </div>

            {loading && <p className="ledger-cal-state">불러오는 중…</p>}
            {error && !loading && (
              <div>
                <p className="ledger-cal-state ledger-cal-state--error">{error}</p>
                <p className="ledger-cal-state">
                  <button
                    type="button"
                    className="home-btn"
                    onClick={() => setReloadKey((k) => k + 1)}
                  >
                    다시 시도
                  </button>
                </p>
              </div>
            )}

            {!error && (
              <div
                className="ledger-history-table-wrap"
                onMouseLeave={() => setPreview(null)}
              >
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
                        className={selected.id === row.id ? "is-selected" : ""}
                        onMouseEnter={() =>
                          setPreview({ day: row.day, month: row.month, id: row.id })
                        }
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
                                SIGNAL_BADGE[row.signal] || "ledger-history-badge--gray"
                              }`}
                            >
                              {row.aiStatus}
                            </span>
                          ) : (
                            // 예외 지출이 아니면 AI 판정 미표시
                            <span className="ledger-history-ai-none" aria-hidden>
                              —
                            </span>
                          )}
                        </td>
                        <td className="ledger-history-amount">{row.amount}</td>
                      </tr>
                    ))}
                    {!loading && expenseRows.length === 0 && (
                      <tr>
                        <td colSpan={5} className="ledger-history-empty-cell">
                          이 기간에는 지출이 없어요.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <div className="ledger-day-col">
            <section className="home-card ledger-day-card">
              <div className="ledger-day-head">
                <h2 className="home-card-title home-card-title--sm">
                  {active ? dayLabel(activeYear, active.month, active.day) : "날짜를 선택하세요"}
                </h2>
                {active && <span className="ledger-day-total">{formatWon(activeDayTotal)}</span>}
              </div>

              <div className="ledger-day-body">
                <ul className="home-tx-list">
                  {activeDayRows.map((row) => (
                    <li key={row.id} className="ledger-detail-list-item">
                      <button
                        type="button"
                        className={`home-tx-row ledger-detail-row ${
                          selected.id === row.id ? "is-selected" : ""
                        }`}
                        onClick={() => selectRow(row)}
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

                {active && activeDayRows.length === 0 && (
                  <div className="ledger-day-empty">
                    <p>이 날짜에는 등록된 내역이 없어요.</p>
                  </div>
                )}

                {activeRow && (
                  <div className="ledger-expense-detail" aria-live="polite">
                    <div className="ledger-expense-detail-head">
                      <p className="ledger-detail-eyebrow">상세 내역</p>
                      <div className="ledger-detail-title-row">
                        <span className="ledger-detail-icon" aria-hidden>
                          {iconFor(activeRow)}
                        </span>
                        <div className="ledger-detail-title-amount">
                          <h3 className="ledger-detail-title">{activeRow.name}</h3>
                          <span className="ledger-detail-amount">{activeRow.amount}</span>
                        </div>
                      </div>
                    </div>

                    <dl className="ledger-detail-grid">
                      <div>
                        <dt>날짜</dt>
                        <dd>{activeRow.date}</dd>
                      </div>
                      <div>
                        <dt>구분</dt>
                        <dd>
                          <span
                            className={`ledger-detail-type ${
                              activeRow.type === "INCOME"
                                ? "ledger-detail-type--income"
                                : "ledger-detail-type--expense"
                            }`}
                          >
                            {activeRow.type === "INCOME" ? "수입" : "지출"}
                          </span>
                        </dd>
                      </div>
                      <div>
                        <dt>카테고리</dt>
                        <dd>{activeRow.cat}</dd>
                      </div>
                      <div>
                        <dt>결제수단</dt>
                        <dd>{activeRow.payment || "—"}</dd>
                      </div>
                    </dl>

                    <div className="ledger-detail-note">
                      <span>메모</span>
                      <p>{activeRow.memo}</p>
                    </div>
                    {activeRow.reason && (
                      <div className="ledger-detail-note">
                        <span>소비 사유</span>
                        <p>{activeRow.reason}</p>
                      </div>
                    )}
                    {activeRow.aiStatus && (
                      <div className="ledger-detail-ai">
                        <span className="ledger-detail-ai-badge">
                          AI 판정 · {activeRow.aiStatus}
                        </span>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </section>
          </div>
        </div>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default ReportPage;
