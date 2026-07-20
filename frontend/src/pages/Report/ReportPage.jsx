import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
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

// 소비 리포트 — 1차 버전(주간 전용). /wallet 보이는 리포트의 "자세히보기"가 이곳으로 온다.
function ReportPage({ user, onLogout }) {
  const navigate = useNavigate();

  const today = useMemo(() => new Date(), []);

  // 이번 주 = 오늘이 속한 주, 일요일 시작 7일.
  const weekDates = useMemo(() => {
    const startOffset = today.getDay(); // 0=일 … 6=토
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(today);
      d.setDate(d.getDate() - startOffset + i);
      return d;
    });
  }, [today]);

  // 이번 주가 걸친 달(최대 2개) — "YYYY-MM" 목록.
  const yearMonths = useMemo(() => {
    const set = new Set();
    weekDates.forEach((d) => set.add(`${d.getFullYear()}-${pad2(d.getMonth() + 1)}`));
    return [...set];
  }, [weekDates]);

  // 주 범위(연/월/일 일치) 판별용 키 집합.
  const weekKeySet = useMemo(
    () => new Set(weekDates.map((d) => `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`)),
    [weekDates],
  );

  const [rows, setRows] = useState([]);
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

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    Promise.all(yearMonths.map((ym) => getMonthlyLedger(ym)))
      .then((results) => {
        if (!alive) return;
        const merged = results.flat();
        const mapped = merged
          .map(toRow)
          .filter((r) => weekKeySet.has(`${r.year}-${r.month}-${r.day}`));
        setRows(mapped);
        const todayRows = mapped.filter(
          (r) => r.day === today.getDate() && r.month === today.getMonth() + 1,
        );
        setSelected({
          day: today.getDate(),
          month: today.getMonth() + 1,
          id: todayRows[0]?.id ?? null,
        });
      })
      .catch((e) => {
        if (!alive) return;
        // 세션 만료 — 거짓 "빈 주" 대신 로그인으로 보냄
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [yearMonths, weekKeySet, navigate, today, reloadKey]);

  const expenseRows = rows.filter((r) => r.type === "EXPENSE");
  const weekExpenseTotal = expenseRows.reduce((sum, r) => sum + r.amountNum, 0);

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
    ? weekDates.find((d) => d.getMonth() + 1 === active.month && d.getDate() === active.day)?.getFullYear() ??
      today.getFullYear()
    : today.getFullYear();

  const selectRow = (row) => {
    setSelected({ day: row.day, month: row.month, id: row.id });
  };

  return (
    <AppShell activeTop="wallet" activeSide="report" user={user} onLogout={onLogout}>
      <main className="home-main">
        <div className="ledger-header">
          <h1 className="ledger-greeting">소비 리포트</h1>
        </div>

        <div className="ledger-row report-week-row">
          <section className="ledger-history-card report-week-table">
            <div className="ledger-history-head">
              <span className="ledger-history-chip">주간 지출 내역</span>
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
