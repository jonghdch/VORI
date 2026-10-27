import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import RecordCalendar, { dateKey } from "../../components/RecordCalendar";
import { getMonthlyLedger } from "../../api/ledger";
import { markMonthlyReportRead } from "../../api/monthlyReports";
import { listInstruments } from "../../api/paymentInstruments";
import IssuerBadge from "../../components/IssuerBadge";
import { SIGNAL_STATUS } from "../../signalLabels";
import "../Home/HomeDashboard.css";
import "../Wallet/WalletPage.css";
import "./ReportPage.css";

// 카테고리 도넛·범례 공용 팔레트 — WalletPage "보이는 리포트" 위젯과 동일 (색상 일관성).
const CHART_COLORS = ["#8fb07c", "#d9a68b", "#e3c38f", "#9db8c6"];

// 합리성 시그널(백엔드 enum) → 한글 상태 + 배지 색상 클래스.
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

// 이름 뒤 조사 「로/으로」 — 받침이 없거나 ㄹ 받침이면 「로」 (K-패스로, 생활통장으로, 지갑로 X → 지갑으로).
function withRo(word) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return `${word}로`; // 한글이 아니면(영문·숫자) 「로」
  const jong = code % 28;
  return jong === 0 || jong === 8 ? `${word}로` : `${word}으로`;
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
    paymentMethod: item.paymentMethod || null,
    paymentInstrumentId: item.paymentInstrumentId ?? null,
    memo: item.memo || "",
    // AI 질문에 답한 소비 사유 — 답변한 예외 지출에만 존재.
    reason: item.reason || "",
  };
}

// 소비 리포트 — 주/월 단위 대시보드. /wallet 보이는 리포트의 "자세히보기"가 이곳으로 온다.
function ReportPage({ user, onLogout }) {
  const navigate = useNavigate();

  const today = useMemo(() => new Date(), []);
  const todayKey = dateKey(today.getFullYear(), today.getMonth() + 1, today.getDate());
  // 「지난 날」 판단의 기준 — 오늘의 끝. 주 보기 날짜는 기준일(anchor)의 시각을 그대로 가져 today 보다
  // 몇 밀리초 늦을 수 있어, today 와 바로 비교하면 오늘이 지난 날에서 빠졌다(「기록한 날 4 / 3일」)
  const endOfToday = useMemo(
    () => new Date(today.getFullYear(), today.getMonth(), today.getDate(), 23, 59, 59, 999),
    [today],
  );

  // 보기 단위(주/월)와 기준일. 이동 시 anchor 만 갱신되고 기간이 따라간다.
  // ?month=2026-09 로 들어오면(알림·홈 말풍선의 "월간 리포트 확인") 그 달 월간 보기로 연다.
  const [searchParams] = useSearchParams();
  const requestedMonth = /^\d{4}-\d{2}$/.test(searchParams.get("month") || "") ? searchParams.get("month") : null;
  const [viewMode, setViewMode] = useState(() => (requestedMonth ? "month" : "week"));
  const [anchor, setAnchor] = useState(() => {
    if (!requestedMonth) return new Date();
    const [y, m] = requestedMonth.split("-").map(Number);
    return new Date(y, m - 1, 1);
  });

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

  // 월간 보기로 어떤 달을 열면 그 달 월간 리포트(정산된 경우)를 열람한 것으로 기록한다.
  // 정산 전인 달이면 서버가 조용히 무시한다.
  useEffect(() => {
    if (viewMode !== "month") return;
    markMonthlyReportRead(`${calYear}-${pad2(calMonth)}`).catch(() => {});
  }, [viewMode, calYear, calMonth]);

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

  // 등록한 카드·계좌 — 결제수단별 지출에서 카드 이름·배지를 붙인다.
  // 목록은 삭제(보관)하지 않은 것만 온다. 못 불러오면(instrumentsFailed) 카드 대신 결제수단 종류로 묶는다 —
  // 그러지 않으면 멀쩡한 카드까지 전부 "삭제한 카드·계좌" 로 보였다.
  const [instruments, setInstruments] = useState([]);
  const [instrumentsFailed, setInstrumentsFailed] = useState(false);
  useEffect(() => {
    listInstruments()
      .then(setInstruments)
      .catch(() => setInstrumentsFailed(true));
  }, []);

  // 직전 기간(지난달 / 지난주) — 스냅샷의 비교 한 줄용. null = 불러오는 중, 실패하면 [] 로 두고 문구를 숨긴다.
  const prevPeriodDates = useMemo(() => {
    if (viewMode === "month") {
      const y = anchor.getFullYear();
      const m = anchor.getMonth() - 1;
      const n = new Date(y, m + 1, 0).getDate();
      return Array.from({ length: n }, (_, i) => new Date(y, m, i + 1));
    }
    return periodDates.map((d) => {
      const p = new Date(d);
      p.setDate(p.getDate() - 7);
      return p;
    });
  }, [viewMode, anchor, periodDates]);
  const [prevRows, setPrevRows] = useState(null);
  const [prevFailed, setPrevFailed] = useState(false);
  useEffect(() => {
    let alive = true;
    setPrevRows(null);
    setPrevFailed(false);
    const months = [...new Set(prevPeriodDates.map((d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`))];
    Promise.all(months.map((ym) => getMonthlyLedger(ym)))
      .then((results) => {
        if (alive) setPrevRows(results.flat().map(toRow));
      })
      .catch(() => {
        if (alive) {
          setPrevRows([]);
          setPrevFailed(true);
        }
      });
    return () => {
      alive = false;
    };
  }, [prevPeriodDates]);

  const expenseRows = rows.filter((r) => r.type === "EXPENSE");
  const incomeRows = rows.filter((r) => r.type === "INCOME");
  const weekExpenseTotal = expenseRows.reduce((sum, r) => sum + r.amountNum, 0);
  const incomeTotal = incomeRows.reduce((sum, r) => sum + r.amountNum, 0);

  // ── 기간 통계 ─────────────────────────────────────────
  // 기록한 날 수 / 기간 일수, 하루 평균(오늘까지 지난 날 기준 — 미래 날짜로 평균을 희석하지 않음).
  const recordedDayCount = new Set(rows.map((r) => dateKey(r.year, r.month, r.day))).size;
  const elapsedCount = periodDates.filter((d) => d <= endOfToday).length; // 0 이면 아직 안 온 기간
  const elapsedDays = Math.max(1, elapsedCount);
  const dailyAvg = Math.round(weekExpenseTotal / elapsedDays);

  // AI 판정(예외 지출) 분포.
  const judged = expenseRows.filter((r) => r.aiJudged && r.signal);
  const signalCount = { GREEN: 0, GRAY: 0, RED: 0 };
  judged.forEach((r) => {
    signalCount[r.signal] += 1;
  });
  const signalTotal = judged.length;

  // 카테고리별 지출 집계 — 상위 4개 + 나머지는 "기타"로 묶어 도넛과 범례가 항상 100%를 설명하게 한다.
  const categoryBreakdown = useMemo(() => {
    const byCat = new Map();
    expenseRows.forEach((r) => {
      byCat.set(r.cat, (byCat.get(r.cat) || 0) + r.amountNum);
    });
    const sorted = [...byCat.entries()].sort((a, b) => b[1] - a[1]);
    const top = sorted.slice(0, 4).map(([label, amount], i) => ({
      label,
      amount,
      pct: weekExpenseTotal > 0 ? Math.round((amount / weekExpenseTotal) * 100) : 0,
      color: CHART_COLORS[i % CHART_COLORS.length],
    }));
    const restAmount = sorted.slice(4).reduce((sum, [, amt]) => sum + amt, 0);
    if (restAmount > 0) {
      top.push({
        label: "기타",
        amount: restAmount,
        pct: weekExpenseTotal > 0 ? Math.round((restAmount / weekExpenseTotal) * 100) : 0,
        color: "#c9c2b3",
      });
    }
    return top;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expenseRows, weekExpenseTotal]);

  // 도넛 = 카테고리 비중 conic-gradient (실데이터 기반).
  const donutGradient = useMemo(() => {
    if (weekExpenseTotal <= 0) return null;
    let acc = 0;
    const stops = categoryBreakdown.map((c) => {
      const from = acc;
      acc += (c.amount / weekExpenseTotal) * 360;
      return `${c.color} ${from}deg ${acc}deg`;
    });
    if (acc < 359.9) stops.push(`#e8e3d8 ${acc}deg 360deg`);
    return `conic-gradient(${stops.join(", ")})`;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categoryBreakdown, weekExpenseTotal]);

  // 순저축 = 수입 − 지출 (마이너스면 이번 기간 적자).
  const netSaved = incomeTotal - weekExpenseTotal;

  // ── 지난달(지난주) 비교 ─────────────────────────────────
  // 오늘이 든 기간이면 지난 기간도 같은 날 수만큼만(1일~오늘 vs 지난달 1일~같은 날), 지나간 기간이면 기간 전체끼리.
  const elapsedInPeriod = elapsedCount;
  const prevUnit = viewMode === "week" ? "지난주" : "지난달";
  const comparison = useMemo(() => {
    if (loading || prevRows == null || prevFailed || elapsedInPeriod === 0) return null;
    const prevKeys = new Set(
      (containsToday ? prevPeriodDates.slice(0, elapsedInPeriod) : prevPeriodDates).map((d) =>
        dateKey(d.getFullYear(), d.getMonth() + 1, d.getDate()),
      ),
    );
    const prevTotal = prevRows
      .filter((r) => r.type === "EXPENSE" && prevKeys.has(dateKey(r.year, r.month, r.day)))
      .reduce((sum, r) => sum + r.amountNum, 0);
    // 지난 기간에 쓴 돈이 없으면 비교하지 않는다 — 이번 달 가입한 사람에게 "지난달보다 N원 더 쓰는 중" 이 뜨던 문제
    if (prevTotal === 0) return null;
    const diff = weekExpenseTotal - prevTotal;
    const span = containsToday ? `${prevUnit} 같은 기간` : prevUnit;
    const text =
      diff === 0
        ? `${span}과 똑같이 ${containsToday ? "쓰고 있어요" : "썼어요"}`
        : `${span}보다 ${formatWon(Math.abs(diff))} ${diff > 0 ? "더" : "덜"} ${containsToday ? "쓰는 중" : "썼어요"}`;
    return { diff, text };
  }, [loading, prevRows, prevFailed, elapsedInPeriod, containsToday, prevPeriodDates, weekExpenseTotal, prevUnit]);

  // ── 결제수단별 지출 · 판정 ─────────────────────────────
  // 등록한 카드를 고른 지출은 카드별로, 안 고른 지출은 결제수단 종류별로 묶는다.
  // 판정은 모든 지출의 신호등(여유·적정·초과) — 위 "AI 판정 분포"는 AI 질문을 거친 지출만 센다.
  const paymentBreakdown = useMemo(() => {
    const byKey = new Map();
    expenseRows.forEach((r) => {
      const inst = r.paymentInstrumentId != null ? instruments.find((i) => i.id === r.paymentInstrumentId) : null;
      // 카드별: 목록에 있는 카드 / 목록에 없는 카드는 삭제(보관)한 것이라 한 줄로 묶는다(이름을 알 수 없어 따로 두면 구분이 안 된다).
      // 목록을 못 불러왔으면 카드를 알 수 없으니 결제수단 종류로 묶는다.
      const byCard = r.paymentInstrumentId != null && !instrumentsFailed;
      const key = !byCard ? `kind-${r.paymentMethod ?? "NONE"}` : inst ? `inst-${inst.id}` : "inst-archived";
      if (!byKey.has(key)) {
        byKey.set(key, {
          key,
          label: byCard
            ? inst?.name ?? "삭제한 카드·계좌"
            : r.paymentMethod
              ? PAYMENT_LABEL[r.paymentMethod]
              : "결제수단 미입력",
          // 목록을 못 불러와 종류로 묶은 경우엔 카드를 고른 지출도 섞여 있어 이 설명을 달지 않는다
          sub: !byCard && !instrumentsFailed && r.paymentMethod ? "카드를 고르지 않은 지출" : null,
          issuer: inst?.issuer ?? null,
          amount: 0,
          count: 0,
          signalAmount: { GREEN: 0, GRAY: 0, RED: 0 },
          signalCount: { GREEN: 0, GRAY: 0, RED: 0 },
        });
      }
      const g = byKey.get(key);
      g.amount += r.amountNum;
      g.count += 1;
      if (r.signal && g.signalCount[r.signal] != null) {
        g.signalCount[r.signal] += 1;
        g.signalAmount[r.signal] += r.amountNum;
      }
    });
    return [...byKey.values()]
      .map((g) => {
        const judgedCount = g.signalCount.GREEN + g.signalCount.GRAY + g.signalCount.RED;
        return {
          ...g,
          pct: weekExpenseTotal > 0 ? Math.round((g.amount / weekExpenseTotal) * 100) : 0,
          judgedCount,
          redPct: judgedCount > 0 ? Math.round((g.signalCount.RED / judgedCount) * 100) : null,
        };
      })
      .sort((a, b) => b.amount - a.amount);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, instruments, instrumentsFailed, weekExpenseTotal]);
  // 초과 비율이 가장 높은 결제수단 — 판정된 지출이 2건 이상인 결제수단이 둘 이상일 때만 비교한다.
  const worstPayment = useMemo(() => {
    const comparable = paymentBreakdown.filter((g) => g.judgedCount >= 2);
    if (comparable.length < 2) return null;
    const worst = [...comparable].sort((a, b) => b.redPct - a.redPct)[0];
    return worst.redPct > 0 ? worst : null;
  }, [paymentBreakdown]);

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
          <h1 className="report-title">
            <span className="report-title-badge" aria-hidden>📊</span>
            <span className="report-title-text">소비 리포트</span>
          </h1>
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

        {/* ── 상단: 스냅샷(통계+도넛+카테고리 범례) · 캘린더/AI 판정 ── */}
        <div className="home-row report-top-row">
          <section className="home-card report-hero-card">
            <h2 className="home-card-title home-card-title--sm report-card-title">
              {viewMode === "week" ? "이번 주 스냅샷" : "이번 달 스냅샷"}
              <span className="report-card-period">{periodLabel}</span>
            </h2>

            <div className="report-hero-stats">
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
                <p className="home-kpi-title">순저축</p>
                {/* 이 기간에 수입이 없으면(수입을 안 적는 학생 대부분) 「−지출 전체」를 빨간 적자로 보이지 않는다 */}
                {incomeRows.length === 0 ? (
                  <>
                    <p className="home-kpi-value">—</p>
                    <p className="home-kpi-sub">이 기간 수입 없음</p>
                  </>
                ) : (
                  <>
                    <p
                      className={`home-kpi-value ${
                        netSaved >= 0 ? "report-kpi-value--pos" : "report-kpi-value--neg"
                      }`}
                    >
                      {netSaved >= 0 ? "+" : "−"}
                      {formatWon(Math.abs(netSaved))}
                    </p>
                    <p className="home-kpi-sub">수입 − 지출</p>
                  </>
                )}
              </div>
              <div className="report-kpi">
                <p className="home-kpi-title">기록한 날</p>
                <p className="home-kpi-value">
                  {recordedDayCount}
                  {/* 아직 안 온 날은 셀 수 없으니 분모에서 뺀다 — 하루 평균과 같은 기준(지난 날).
                      통째로 안 온 기간이면 분모가 없다 */}
                  <span className="report-kpi-unit">/ {elapsedCount > 0 ? `${elapsedDays}일` : "—"}</span>
                </p>
                <p className="home-kpi-sub">하루 평균 {formatWon(dailyAvg)}</p>
              </div>
            </div>

            {comparison && (
              <p
                className={`report-compare ${
                  comparison.diff > 0 ? "report-compare--up" : comparison.diff < 0 ? "report-compare--down" : ""
                }`}
              >
                {comparison.text}
              </p>
            )}

            <div className="report-hero-body">
              <div className="report-donut-wrap">
                {donutGradient ? (
                  <div className="report-donut" style={{ background: donutGradient }}>
                    <div className="report-donut-hole">
                      <span className="report-donut-total">{formatWon(weekExpenseTotal)}</span>
                      <span className="report-donut-label">총 지출</span>
                    </div>
                  </div>
                ) : (
                  <div className="report-donut report-donut--empty">
                    <div className="report-donut-hole">
                      <span className="report-donut-label">
                        {loading ? "불러오는 중…" : "지출 없음"}
                      </span>
                    </div>
                  </div>
                )}
              </div>

              <ul className="report-cat-legend-list">
                {categoryBreakdown.length === 0 ? (
                  <li className="report-signal-empty">이 {unitLabel}에는 지출이 없어요.</li>
                ) : (
                  categoryBreakdown.map((c) => (
                    <li key={c.label} className="report-cat-legend-row">
                      <span
                        className="report-cat-legend-dot"
                        style={{ background: c.color }}
                        aria-hidden
                      />
                      <span className="report-cat-legend-label">{c.label}</span>
                      <div className="home-stat-track report-cat-track">
                        <div
                          className="home-stat-fill"
                          style={{ width: `${c.pct}%`, background: c.color }}
                        />
                      </div>
                      <span className="report-cat-amount">
                        {formatWon(c.amount)}
                        <small> {c.pct}%</small>
                      </span>
                    </li>
                  ))
                )}
              </ul>
            </div>
          </section>

          <div className="report-side-col">
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

            <section className="home-card report-signal-card">
              <h2 className="home-card-title home-card-title--sm report-card-title">
                {/* 판정은 규칙이 한다 — AI 가 이유를 물은 지출의 신호만 세는 카드라 「질문 결과」로 부른다.
                    하루 판정(1차·최종)과 같은 「판정」을 쓰면 둘이 섞인다 */}
                질문 결과
                <span className="report-card-period">{signalTotal}건</span>
              </h2>
              {signalTotal === 0 ? (
                <p className="report-signal-empty">이 {unitLabel}에는 이유를 물어본 지출이 없어요.</p>
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
            </section>
          </div>
        </div>

        <section className="home-card report-pay-card" aria-labelledby="report-pay-title">
          <div className="report-pay-head">
            <h2 id="report-pay-title" className="home-card-title home-card-title--sm report-card-title">
              결제수단별 지출
              <span className="report-card-period">{periodLabel}</span>
            </h2>
            <button type="button" className="report-pay-manage" onClick={() => navigate("/settings/payment")}>
              카드·계좌 관리 ›
            </button>
          </div>
          {worstPayment && (
            <p className="report-pay-insight">
              <strong>{withRo(worstPayment.label)}</strong> 쓴 지출의 초과 비율이 가장 높아요 ({worstPayment.redPct}%).
            </p>
          )}
          {paymentBreakdown.length === 0 ? (
            <p className="report-signal-empty">
              {loading ? "불러오는 중…" : `이 ${unitLabel}에는 지출이 없어요.`}
            </p>
          ) : (
            <ul className="report-pay-list">
              {paymentBreakdown.map((g) => (
                <li key={g.key} className="report-pay-row">
                  <span className="report-pay-name">
                    {g.issuer ? (
                      <IssuerBadge issuer={g.issuer} size={26} />
                    ) : (
                      <span className="report-pay-kind-dot" aria-hidden />
                    )}
                    <span className="report-pay-name-text">
                      <span>{g.label}</span>
                      {g.sub && <small>{g.sub}</small>}
                    </span>
                  </span>
                  {/* 막대 길이 = 전체 지출 중 비중, 안의 색 = 판정별 금액 */}
                  <span
                    className="report-pay-track"
                    role="img"
                    aria-label={`${g.label} ${formatWon(g.amount)}, 전체의 ${g.pct}%. 여유 ${g.signalCount.GREEN}건, 적정 ${g.signalCount.GRAY}건, 초과 ${g.signalCount.RED}건`}
                  >
                    <span className="report-pay-fill" style={{ width: `${Math.max(g.pct, 2)}%` }}>
                      {["GREEN", "GRAY", "RED"].map((sig) =>
                        g.signalAmount[sig] > 0 ? (
                          <span
                            key={sig}
                            className={`report-pay-seg report-signal-seg--${sig.toLowerCase()}`}
                            style={{ flexGrow: g.signalAmount[sig] }}
                          />
                        ) : null,
                      )}
                      {g.judgedCount < g.count && (
                        <span
                          className="report-pay-seg report-pay-seg--none"
                          style={{
                            flexGrow: g.amount - g.signalAmount.GREEN - g.signalAmount.GRAY - g.signalAmount.RED,
                          }}
                        />
                      )}
                    </span>
                  </span>
                  <span className="report-pay-amount">
                    {formatWon(g.amount)}
                    <small>
                      {g.pct}% · {g.count}건
                      {g.redPct != null && g.redPct > 0 && (
                        <b className="report-pay-red"> · 초과 {g.redPct}%</b>
                      )}
                    </small>
                  </span>
                </li>
              ))}
            </ul>
          )}
          <ul className="report-signal-legend report-pay-legend" aria-hidden>
            {["GREEN", "GRAY", "RED"].map((sig) => (
              <li key={sig}>
                <i className={`report-signal-dot report-signal-seg--${sig.toLowerCase()}`} />
                {SIGNAL_STATUS[sig]}
              </li>
            ))}
          </ul>
        </section>

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
                      <th>질문 결과</th>
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
                          질문 결과 · {activeRow.aiStatus}
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
