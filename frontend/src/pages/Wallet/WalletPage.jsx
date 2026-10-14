import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import Icon from "../../components/Icon";
import useMediaQuery from "../../components/useMediaQuery";
import SignalChip, { SIGNAL_META, categoryIcon, signalForUsage } from "../../components/SignalChip";
import { getMonthlyLedger } from "../../api/ledger";
import { getMonthlyJudgments } from "../../api/dailyJudgment";
import { listInquiriesByDate } from "../../api/inquiries";
import { getSpendingPlan } from "../../api/spendingPlan";
import { AI_ACTIVE_FROM_HOUR, canUseAiJudge } from "../../config";
import CoinIcon from "../../components/CoinIcon";
import { SIGNAL_STATUS } from "../../signalLabels";
import { listInstruments } from "../../api/paymentInstruments";
import { issuerOf } from "../../constants/issuers";
import { CATEGORIES_BY_TYPE, getCategoryLabel } from "../WalletEntry/utils";
import LedgerFilterSheet, { EMPTY_LIST_FILTER, isListFilterActive } from "./LedgerFilterSheet";
import LedgerSearchSheet, { SearchGlyph } from "./LedgerSearchSheet";
import LedgerSheet from "./LedgerSheet";
import ListTxDetail from "./ListTxDetail";
// .home-main(본문 틀)은 HomeDashboard.css, 카드·버튼·판정 칩은 홈과 같은 HomeScreen.css(.hm-*)를 쓴다.
// 가계부에만 있는 달력·하루 카드·내역은 WalletScreen.css(.lw-*).
import "../Home/HomeDashboard.css";
import "../Home/HomeScreen.css";
import "./WalletScreen.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 카테고리 막대 공용 팔레트 (상위 카테고리 순서대로 순환).
// 베이지 화면에 튀지 않게 채도를 낮춘 세이지·클레이·샌드·더스티블루.
const CHART_COLORS = [
  "#8fb07c",
  "#d9a68b",
  "#e3c38f",
  "#9db8c6",
];

// 판정 보상 스탯 — 홈 반려 스탯과 같은 이름·색. 이모지 대신 색 점 + 글자.
const STAT_REWARDS = [
  { key: "ENERGY", label: "에너지", color: "#7fb069" },
  { key: "CHARM", label: "매력", color: "#e07a7a" },
  { key: "IQ", label: "지능", color: "#6b9bd1" },
  { key: "ENDURANCE", label: "지구력", color: "#c9913f" },
];

// 합리성 시그널(백엔드 enum) → 한글 상태.
// 1차 판정만 끝나고 예외 지출 사유를 기다리는 날(PENDING)은 아직 그날 결과가 아니다 — 색·보상을 확정된 날처럼 보이지 않는다.
const isFinalJudgment = (judgment) => Boolean(judgment) && judgment.status !== "PENDING";

// 결제수단 enum(백엔드 PaymentMethod) → 한글 라벨.
const PAYMENT_LABEL = {
  CASH: "현금",
  DEBIT: "체크카드",
  CREDIT: "신용카드",
  TRANSFER: "계좌이체",
  MOBILE_PAY: "모바일페이",
};

// 카테고리/타입 → 선 아이콘 (백엔드가 아이콘을 주지 않으므로 프론트에서 매핑).
function iconFor(row) {
  if (row.type === "INCOME") return "wallet";
  return categoryIcon(row.cat);
}

// 목록·검색 행의 카테고리 글자 — 수입은 출처 enum(ALLOWANCE 등)이 오므로 입력 화면과 같은 한글 라벨로
function rowCategoryLabel(row) {
  if (row.type === "INCOME") return getCategoryLabel(row.cat, "income") || "수입";
  return row.cat !== "—" ? row.cat : "미분류";
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function formatWon(value) {
  return `${value.toLocaleString("ko-KR")}원`;
}

// 달력 칸에 들어가는 짧은 금액 — 15000 → "1.5만", 4500 → "4,500"
function shortWon(value) {
  if (value >= 10000) return `${Math.round(value / 1000) / 10}만`;
  return value.toLocaleString("ko-KR");
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

function toIsoDate(year, month, day) {
  return `${year}-${pad2(month)}-${pad2(day)}`;
}

// 백엔드 LedgerResponse → 화면이 쓰는 행 형태로 변환.
function toRow(item) {
  const day = Number(item.date.split("-")[2]);
  return {
    id: `${item.type}-${item.id}`,
    dbId: item.id,
    type: item.type, // "EXPENSE" | "INCOME"
    day,
    name: item.item || (item.type === "INCOME" ? "수입" : "지출"),
    cat: item.category || "—",
    amountNum: item.amount,
    amount: formatWon(item.amount),
    date: formatDateDisplay(item.date),
    dateIso: item.date,
    signal: item.signal, // GREEN | GRAY | RED | null
    // AI 판정은 예외적 지출(aiJudged)에만. 평소 지출은 배지 미표시.
    aiJudged: Boolean(item.aiJudged),
    aiStatus: item.aiJudged && item.signal ? SIGNAL_STATUS[item.signal] : null,
    payment: item.paymentMethod ? PAYMENT_LABEL[item.paymentMethod] : null,
    paymentInstrumentId: item.paymentInstrumentId ?? null,
    memo: item.memo || "",
    // AI 질문에 답한 소비 사유 — 답변한 예외 지출에만 존재.
    reason: item.reason || "",
  };
}

// 달력 한 칸 — 날짜 · 그날 지출(짧게) · 판정 아이콘. 판정은 아이콘 모양 + 아래 범례 글자로도 구분된다.
function CalendarCell({ day, weekday, rows, signal, judged, isToday, isSelected, isFuture, onSelect, month }) {
  const expense = rows.filter((r) => r.type === "EXPENSE").reduce((s, r) => s + r.amountNum, 0);
  const income = rows.filter((r) => r.type === "INCOME").reduce((s, r) => s + r.amountNum, 0);
  const meta = signal ? SIGNAL_META[signal] : null;
  const label = [
    `${month}월 ${day}일`,
    isToday ? "오늘" : null,
    expense > 0 ? `지출 ${formatWon(expense)}` : null,
    income > 0 ? `수입 ${formatWon(income)}` : null,
    meta ? meta.text : null,
    judged ? "판정 완료" : null,
  ]
    .filter(Boolean)
    .join(", ");
  return (
    <button
      type="button"
      className={[
        "lw-cell",
        weekday === 0 ? "is-sun" : "",
        weekday === 6 ? "is-sat" : "",
        isToday ? "is-today" : "",
        isSelected ? "is-selected" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      aria-pressed={isSelected}
      aria-label={label}
      disabled={isFuture}
      onClick={onSelect}
    >
      <span className="lw-cell-day">{day}</span>
      {expense > 0 ? (
        <span className="lw-cell-amt">-{shortWon(expense)}</span>
      ) : income > 0 ? (
        <span className="lw-cell-amt lw-cell-amt--in">+{shortWon(income)}</span>
      ) : null}
      {meta && (
        <span className={`lw-cell-sig lw-cell-sig--${meta.tone} ${judged ? "is-final" : ""}`}>
          <Icon name={meta.icon} size={12} strokeWidth={2.4} />
        </span>
      )}
    </button>
  );
}

function WalletPage({ user, onLogout }) {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  // [날짜 | 목록] — 날짜: 달력·하루 카드(기본), 목록: 기간의 수입·지출 내역만. ?view=list 로 새로고침·뒤로가기에도 유지.
  const listView = params.get("view") === "list";
  const setListView = (on) => {
    const next = new URLSearchParams(params);
    if (on) next.set("view", "list");
    else next.delete("view");
    setParams(next);
  };
  const selectedDateParam = params.get("date");

  // 보고 있는 달 (1-based month). 처음엔 실제 이번 달부터.
  const today = useMemo(() => new Date(), []);
  const [demoDate, setDemoDate] = useState(() => toIsoDate(today.getFullYear(), today.getMonth() + 1, today.getDate()));
  const initialDate = useMemo(() => {
    if (!selectedDateParam || !/^\d{4}-\d{2}-\d{2}$/.test(selectedDateParam)) {
      return today;
    }
    const [y, m, d] = selectedDateParam.split("-").map(Number);
    const parsed = new Date(y, m - 1, d);
    const isValid =
      !Number.isNaN(parsed.getTime()) &&
      parsed.getFullYear() === y &&
      parsed.getMonth() === m - 1 &&
      parsed.getDate() === d;
    return isValid && parsed <= today ? parsed : today;
  }, [selectedDateParam, today]);
  const [viewYear, setViewYear] = useState(initialDate.getFullYear());
  const [viewMonth, setViewMonth] = useState(initialDate.getMonth() + 1);

  // 주/월 보기 전환
  const [viewMode, setViewMode] = useState("month");

  // 지출 내역 페이지네이션
  const [historyPage, setHistoryPage] = useState(1);

  // 달 경계를 넘는 주 이동 시 fetch 후 선택할 날짜를 임시 보관
  const pendingDayRef = useRef(null);
  // 검색에서 다른 달 내역을 고르면, 그 달을 불러온 뒤 펼칠 행 id
  const pendingOpenRef = useRef(null);

  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [judgmentsByDate, setJudgmentsByDate] = useState({});
  const [judgmentLoading, setJudgmentLoading] = useState(true);
  const [judgmentError, setJudgmentError] = useState(null);

  // 기본 선택 = 오늘 (이번 달 한정)
  const [selectedDay, setSelectedDay] = useState(() => initialDate.getDate());
  // 하루 카드에서 펼쳐 둔 내역 (상세). null = 모두 접힘.
  const [selectedId, setSelectedId] = useState(null);
  // 일반 사용자는 20시부터, 관리자는 시연·검증을 위해 항상 사용할 수 있다.
  const [isAiActive, setIsAiActive] = useState(() => canUseAiJudge(user));

  useEffect(() => {
    const id = setInterval(() => {
      setIsAiActive(canUseAiJudge(user));
    }, 60_000);
    return () => clearInterval(id);
  }, [user]);

  // 달이 바뀌면 그 달 데이터를 다시 불러오고 선택 초기화.
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    setJudgmentLoading(true);
    setJudgmentError(null);
    const isCurrentMonth =
      viewYear === today.getFullYear() && viewMonth === today.getMonth() + 1;
    const isInitialParamMonth =
      selectedDateParam &&
      viewYear === initialDate.getFullYear() &&
      viewMonth === initialDate.getMonth() + 1;
    // 주 이동으로 달 경계를 넘은 경우 pendingDayRef 에 저장된 날짜 우선 사용
    const pending = pendingDayRef.current;
    pendingDayRef.current = null;
    const defaultDay =
      pending ?? (isInitialParamMonth ? initialDate.getDate() : isCurrentMonth ? today.getDate() : 1);
    setSelectedDay(defaultDay);
    setSelectedId(null);
    getMonthlyLedger(`${viewYear}-${pad2(viewMonth)}`)
      .then((data) => {
        if (!alive) return;
        setRows(Array.isArray(data) ? data.map(toRow) : []);
        const openId = pendingOpenRef.current;
        pendingOpenRef.current = null;
        if (openId) {
          setSelectedId(openId);
          requestAnimationFrame(() => dayCardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
        }
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
    getMonthlyJudgments(`${viewYear}-${pad2(viewMonth)}`)
      .then((data) => {
        if (!alive) return;
        const next = {};
        (Array.isArray(data) ? data : []).forEach((judgment) => {
          next[judgment.date] = judgment;
        });
        setJudgmentsByDate(next);
      })
      .catch(() => {
        if (alive) {
          setJudgmentsByDate({});
          setJudgmentError("판정 보상 정보를 불러오지 못했어요. 백엔드를 다시 시작해 주세요.");
        }
      })
      .finally(() => {
        if (alive) setJudgmentLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [viewYear, viewMonth, navigate, today, selectedDateParam, initialDate]);

  // 이번 달 사용 가능 예산 — 프로필 수입·고정비·설문으로 계산된 스탯별 예산의 합.
  // undefined = 불러오는 중, null = 실패. 실패해도 지출 숫자는 그대로 보여 준다.
  const [plan, setPlan] = useState(undefined);
  useEffect(() => {
    let alive = true;
    setPlan(undefined);
    getSpendingPlan(`${viewYear}-${pad2(viewMonth)}`)
      .then((p) => alive && setPlan(p ?? null))
      .catch(() => alive && setPlan(null));
    return () => {
      alive = false;
    };
  }, [viewYear, viewMonth]);
  const monthBudget = Object.values(plan?.budgets || {}).reduce(
    (sum, amount) => sum + Number(amount || 0),
    0,
  );

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

  // 일일 판정이 끝난 날은 확정 판정을 우선하고, 그 외에는 지출 신호를 사용한다.
  const signalByDay = useMemo(() => {
    const rank = { GREEN: 1, GRAY: 2, RED: 3 };
    const map = new Map();
    rows.forEach((row) => {
      if (row.type !== "EXPENSE" || !row.signal) return;
      const current = map.get(row.day);
      if (!current || rank[row.signal] > rank[current]) map.set(row.day, row.signal);
    });
    Object.values(judgmentsByDate).forEach((judgment) => {
      const day = Number(judgment.date?.split("-")[2]);
      if (day && judgment.signal && isFinalJudgment(judgment)) map.set(day, judgment.signal);
    });
    return map;
  }, [rows, judgmentsByDate]);

  // 등록한 카드·계좌 — 목록 행에 결제수단 대신 카드 이름·색 점을 붙인다. 못 불러와도 결제수단 종류로 보인다.
  const [instruments, setInstruments] = useState([]);
  useEffect(() => {
    listInstruments().then(setInstruments).catch(() => {});
  }, []);
  const instrumentOf = (row) =>
    row.paymentInstrumentId != null ? instruments.find((i) => i.id === row.paymentInstrumentId) ?? null : null;

  const expenseRows = rows.filter((r) => r.type === "EXPENSE");
  const monthExpenseTotal = expenseRows.reduce((sum, r) => sum + r.amountNum, 0);
  const incomeRows = rows.filter((r) => r.type === "INCOME");
  const monthIncomeTotal = incomeRows.reduce((sum, r) => sum + r.amountNum, 0);
  // AI 질문을 거친 예외 지출 수 (이 달 기준).
  const aiJudgedCount = expenseRows.filter((r) => r.aiJudged).length;

  // 이번 달 예산 대비 — 핵심 숫자 아래 막대·판정 칩에 쓴다. 홈과 같은 85%/100% 기준.
  const budgetPct = monthBudget > 0 ? Math.round((monthExpenseTotal / monthBudget) * 100) : 0;
  const budgetSignal = monthBudget > 0 ? signalForUsage(monthExpenseTotal, monthBudget) : null;

  // 카테고리별 지출 집계 — 금액 내림차순 상위 4개.
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
  const selectedDayExpenses = selectedDayRows.filter((r) => r.type === "EXPENSE");
  const selectedDaySignal = selectedDay ? signalByDay.get(selectedDay) ?? null : null;

  const selectedDateIso = selectedDay
    ? toIsoDate(viewYear, viewMonth, selectedDay)
    : null;
  const todayIso = toIsoDate(
    today.getFullYear(),
    today.getMonth() + 1,
    today.getDate(),
  );
  const selectedDateIsFuture = selectedDateIso && selectedDateIso > todayIso;
  const selectedIsToday = selectedDateIso === todayIso;
  const selectedJudgment = selectedDateIso ? judgmentsByDate[selectedDateIso] ?? null : null;

  const handleAddOrEditExpense = () => {
    if (!selectedDateIso || selectedDateIsFuture) return;
    const editQuery = selectedDayExpenses.length > 0 ? "&edit=true" : "";
    navigate(`/wallet/new?date=${selectedDateIso}${editQuery}`);
  };

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
    if (y > today.getFullYear() || (y === today.getFullYear() && m > today.getMonth() + 1)) {
      return;
    }
    setViewYear(y);
    setViewMonth(m);
  };

  const changeWeek = (delta) => {
    const cur = new Date(viewYear, viewMonth - 1, selectedDay ?? 1);
    cur.setDate(cur.getDate() + delta * 7);
    const y = cur.getFullYear();
    const m = cur.getMonth() + 1;
    const d = cur.getDate();
    if (cur > today) return;
    if (y === viewYear && m === viewMonth) {
      selectDay(d);
      return;
    }
    pendingDayRef.current = d;
    setViewYear(y);
    setViewMonth(m);
  };

  const selectDay = (day) => {
    if (toIsoDate(viewYear, viewMonth, day) > todayIso) return;
    setSelectedDay(day);
    setSelectedId(null);
  };

  // 내역 목록에서 고르면 그날로 옮기고 그 내역을 펼친 채 하루 카드로 올라간다.
  const dayCardRef = useRef(null);
  const selectRow = (row) => {
    setSelectedDay(row.day);
    setSelectedId(row.id);
    dayCardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  // 주 보기 — selectedDay 가 속한 주의 7일 배열 (일요일 시작)
  const weekDates = useMemo(() => {
    const base = new Date(viewYear, viewMonth - 1, selectedDay ?? 1);
    const startOffset = base.getDay(); // 0=일 … 6=토
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(base);
      d.setDate(d.getDate() - startOffset + i);
      return d;
    });
  }, [viewYear, viewMonth, selectedDay]);

  // 표시 범위의 지출 — 월 모드는 달 전체, 주 모드는 보고 있는 주(이번 달 날짜만)
  const visibleExpenseRows = useMemo(() => {
    if (viewMode !== "week") return expenseRows;
    const weekDaySet = new Set(
      weekDates
        .filter((d) => d.getFullYear() === viewYear && d.getMonth() + 1 === viewMonth)
        .map((d) => d.getDate()),
    );
    return expenseRows.filter((r) => weekDaySet.has(r.day));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewMode, weekDates, rows]);
  const visibleExpenseTotal = visibleExpenseRows.reduce((s, r) => s + r.amountNum, 0);

  // 표시 범위가 바뀌면 1페이지로 리셋
  useEffect(() => {
    setHistoryPage(1);
  }, [viewMode, viewYear, viewMonth, weekDates]);

  const HISTORY_PAGE_SIZE = 10;
  const historyTotalPages = Math.max(1, Math.ceil(visibleExpenseRows.length / HISTORY_PAGE_SIZE));
  const safeHistoryPage = Math.min(historyPage, historyTotalPages);
  const pagedExpenseRows = visibleExpenseRows.slice(
    (safeHistoryPage - 1) * HISTORY_PAGE_SIZE,
    safeHistoryPage * HISTORY_PAGE_SIZE,
  );

  // 날짜 네비 표시 문자열
  const dateDisplayLabel = useMemo(() => {
    if (viewMode === "month") {
      return `${viewYear}년 ${viewMonth}월`;
    }
    const first = weekDates[0];
    const last = weekDates[6];
    return `${first.getMonth() + 1}월 ${first.getDate()}일 ~ ${last.getMonth() + 1}월 ${last.getDate()}일`;
  }, [viewMode, viewYear, viewMonth, weekDates]);

  // ── 목록 탭 필터·검색 ──
  // 필터는 이 화면에서만(주소에 안 남김). 기간은 [주|월] 이 맡는다.
  const [listFilter, setListFilter] = useState(EMPTY_LIST_FILTER);
  const [sheet, setSheet] = useState(null); // "filter" | "search" | null
  const closeSheet = useCallback(() => setSheet(null), []);
  const listFilterActive = isListFilterActive(listFilter);
  // 필터 시트의 지출 카테고리 — 자주 쓰는 기본값 + 이 달에 실제로 쓰인 카테고리
  const categoryOptions = useMemo(() => {
    const set = new Set(["식비", "카페", "교통", "쇼핑", "문화", "생활", "의료", "교육", "고정비"]);
    rows.forEach((r) => {
      if (r.type === "EXPENSE" && r.cat && r.cat !== "—") set.add(r.cat);
    });
    return [...set];
  }, [rows]);
  // 검색 시트가 최근 몇 달을 불러올 때 — 검색어가 카테고리 한글 이름에도 걸리게 catLabel 을 붙인다
  const loadSearchMonth = useCallback(
    (ym) =>
      getMonthlyLedger(ym).then((data) =>
        (Array.isArray(data) ? data : []).map(toRow).map((r) => ({ ...r, catLabel: rowCategoryLabel(r) })),
      ),
    [],
  );

  // ── 목록 탭 — 보고 있는 기간(월 / 주)의 수입·지출, 최신 날짜가 위로, 날짜별로 묶는다 ──
  const listGroups = useMemo(() => {
    const inRange =
      viewMode === "week"
        ? new Set(
            weekDates
              .filter((d) => d.getFullYear() === viewYear && d.getMonth() + 1 === viewMonth)
              .map((d) => d.getDate()),
          )
        : null;
    const f = listFilter;
    const passes = (r) =>
      f.types[r.type] &&
      (f.instrumentId == null || r.paymentInstrumentId === f.instrumentId) &&
      (r.type !== "EXPENSE" || f.categories == null || f.categories.includes(r.cat)) &&
      (r.type !== "INCOME" || f.incomeSources == null || f.incomeSources.includes(r.cat));
    const sorted = rows
      .filter((r) => (!inRange || inRange.has(r.day)) && passes(r))
      .sort((a, b) => b.day - a.day);
    const groups = [];
    sorted.forEach((r) => {
      const last = groups[groups.length - 1];
      if (last && last.day === r.day) last.rows.push(r);
      else groups.push({ day: r.day, rows: [r] });
    });
    return groups.map((g) => ({
      ...g,
      income: g.rows.filter((r) => r.type === "INCOME").reduce((sum, r) => sum + r.amountNum, 0),
      expense: g.rows.filter((r) => r.type === "EXPENSE").reduce((sum, r) => sum + r.amountNum, 0),
    }));
  }, [rows, viewMode, weekDates, viewYear, viewMonth, listFilter]);
  const listIncome = listGroups.reduce((sum, g) => sum + g.income, 0);
  const listExpense = listGroups.reduce((sum, g) => sum + g.expense, 0);
  const listCount = listGroups.reduce((sum, g) => sum + g.rows.length, 0);
  // 목록에서 고르면 날짜 탭으로 넘어가 그날 카드에서 그 내역을 펼친다 (하루 카드가 그려진 뒤 스크롤).
  const openInDateView = (row) => {
    setDetail(null);
    setListView(false);
    setSelectedDay(row.day);
    setSelectedId(row.id);
    requestAnimationFrame(() => dayCardRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
  };

  // 검색 결과 고르기 — 지금 보는 달이면 바로, 다른 달이면 그 달로 옮겨 불러온 뒤 그날 카드에서 펼친다.
  const openSearchResult = (row) => {
    setSheet(null);
    const [y, m] = row.dateIso.split("-").map(Number);
    if (y === viewYear && m === viewMonth) {
      openInDateView(row);
      return;
    }
    setListView(false);
    pendingDayRef.current = row.day;
    pendingOpenRef.current = row.id;
    setViewYear(y);
    setViewMonth(m);
  };

  // ── 목록에서 고른 내역의 상세 — 목록 칸이 넓으면 오른쪽 패널, 좁으면(폰) 아래 시트 ──
  // 판단은 창 폭이 아니라 본문(main) 폭으로 한다. PC 는 양옆 메뉴가 본문을 좁혀서, 창이 넓어도 본문이 좁을 수 있다.
  // (목록 칸 자체는 평소 720px 로 묶여 있어 그 폭으론 판단할 수 없다.)
  // 740px = 목록(최소 ~360px) + 패널(360px) + 사이 간격이 나란히 들어가는 본문 폭.
  const listViewRef = useRef(null);
  const [listWide, setListWide] = useState(false);
  useEffect(() => {
    const main = listViewRef.current?.closest("main");
    if (!main || typeof ResizeObserver === "undefined") return undefined;
    const ro = new ResizeObserver(([entry]) => setListWide(entry.contentRect.width >= 740));
    ro.observe(main);
    return () => ro.disconnect();
  }, [listView]);
  // detail = { row, closing } — closing 이면 패널이 접히는 중(짧은 애니메이션 뒤 내린다).
  const [detail, setDetail] = useState(null);
  const detailOpenerRef = useRef(null);
  const reduceMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  // returnFocus: 키보드(Esc)로 닫을 때만 고른 줄로 포커스를 돌린다 — 바깥을 눌러 닫으면 누른 곳에 그대로 둔다.
  const closeDetail = useCallback(
    (returnFocus = false) => {
      const opener = detailOpenerRef.current;
      if (reduceMotion) setDetail(null);
      else {
        setDetail((d) => (d ? { ...d, closing: true } : d));
        setTimeout(() => setDetail((d) => (d?.closing ? null : d)), 160);
      }
      if (returnFocus && opener instanceof HTMLElement) requestAnimationFrame(() => opener.focus());
    },
    [reduceMotion],
  );
  const detailPanelRef = useRef(null);
  const pickListRow = (row, e) => {
    if (detail && !detail.closing && detail.row.id === row.id) {
      closeDetail(false);
      return;
    }
    detailOpenerRef.current = e?.currentTarget ?? null;
    setDetail({ row, closing: false });
  };
  // 달·주를 옮기면 그 기간에 없는 내역이니 닫는다
  useEffect(() => {
    setDetail(null);
  }, [viewYear, viewMonth, viewMode]);
  // 패널(넓은 화면)은 닫기 버튼이 없다 — 패널 바깥을 누르거나 Esc 로 닫는다.
  // 목록 줄(.hm-tx)을 누른 건 줄 자신의 클릭이 처리한다(다른 줄 = 내용 바꾸기, 같은 줄 = 접기) — 여기선 무시.
  // 시트는 LedgerSheet 가 바깥 누르기·Esc·포커스를 맡는다. 필터·검색 시트가 떠 있으면 그쪽이 먼저.
  useEffect(() => {
    if (!detail || detail.closing || !listWide || sheet) return undefined;
    const onKey = (e) => {
      if (e.key === "Escape") closeDetail(true);
    };
    const onPointer = (e) => {
      const t = e.target;
      if (!(t instanceof Element)) return;
      if (detailPanelRef.current?.contains(t)) return;
      if (t.closest(".lw-list-card .hm-tx")) return;
      closeDetail(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("touchstart", onPointer, { passive: true });
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("touchstart", onPointer);
    };
  }, [detail, listWide, sheet, closeDetail]);
  const renderDetail = (row) => (
    <ListTxDetail
      row={row}
      iconName={iconFor(row)}
      categoryLabel={rowCategoryLabel(row)}
      instrument={instrumentOf(row)}
      onOpenDate={() => openInDateView(row)}
      onEdit={() => navigate(`/wallet/new?date=${row.dateIso}&edit=true`)}
    />
  );

  // 목록·검색이 같이 쓰는 행 — 아이콘 · 이름 / 카테고리 · 카드(색 점) · 금액 · 판정 칩
  const renderListRow = (row, onPick, selectable = false) => {
    const inst = instrumentOf(row);
    const selected = selectable && detail && !detail.closing && detail.row.id === row.id;
    return (
      <li key={row.id}>
        <button
          type="button"
          className={`hm-tx ${selected ? "is-selected" : ""}`}
          aria-pressed={selectable ? Boolean(selected) : undefined}
          onClick={(e) => onPick(row, e)}
        >
          <span className="hm-tx-icon">
            <Icon name={iconFor(row)} size={18} />
          </span>
          <span className="hm-tx-mid">
            <span className="hm-tx-name">{row.name}</span>
            <span className="hm-tx-cat">
              {rowCategoryLabel(row)}
              {(inst || row.payment) && (
                <span className="lw-list-pay">
                  {inst && (
                    <span className="lw-list-dot" style={{ background: issuerOf(inst.issuer).color }} aria-hidden />
                  )}
                  {inst ? inst.name : row.payment}
                </span>
              )}
            </span>
          </span>
          <span className="hm-tx-right">
            <span className={`hm-tx-amount ${row.type === "INCOME" ? "lw-amt-in" : ""}`}>
              {row.type === "INCOME" ? "+" : "-"}
              {row.amount}
            </span>
            {row.aiStatus && <SignalChip signal={row.signal} />}
          </span>
        </button>
      </li>
    );
  };

  const nextDisabled =
    viewMode === "month"
      ? viewYear === today.getFullYear() && viewMonth === today.getMonth() + 1
      : (() => {
          const next = new Date(viewYear, viewMonth - 1, selectedDay ?? 1);
          next.setDate(next.getDate() + 7);
          return next > today;
        })();

  // 판정 버튼 — 판정은 오늘 소비를 본다. 관리자는 시연할 날짜를 골라 아무 날이나 볼 수 있다.
  const isAdmin = user?.role === "ADMIN";
  const showJudgeButton = isAdmin || selectedIsToday;
  const judgeLabel = !isAiActive
    ? `${AI_ACTIVE_FROM_HOUR}시부터 판정`
    : isFinalJudgment(selectedJudgment)
      ? "판정 보기"
      : "판정 받기";

  // 하루 카드 아래 판정·보상 안내 상자
  const rewardStatus = !selectedJudgment
    ? "아직 판정 전"
    : isFinalJudgment(selectedJudgment)
      ? "판정 완료"
      : selectedDateIso < todayIso
        ? "1차 판정으로 확정 중"
        : "사유 답변 대기";

  // 기간 이동 — 날짜·목록 탭이 같이 쓴다
  const periodNav = (
    <nav className="hm-card lw-period" aria-label={viewMode === "week" ? "주 선택" : "달 선택"}>
      <button
        type="button"
        className="hm-icon-btn"
        aria-label={viewMode === "week" ? "이전 주" : "이전 달"}
        onClick={() => (viewMode === "week" ? changeWeek(-1) : changeMonth(-1))}
      >
        <Icon name="chevronLeft" size={18} />
      </button>
      <span className="lw-period-label">
        <Icon name="calendar" size={16} />
        {dateDisplayLabel}
      </span>
      <button
        type="button"
        className="hm-icon-btn"
        aria-label={viewMode === "week" ? "다음 주" : "다음 달"}
        onClick={() => (viewMode === "week" ? changeWeek(1) : changeMonth(1))}
        disabled={nextDisabled}
      >
        <Icon name="chevronRight" size={18} />
      </button>
    </nav>
  );

  return (
    <AppShell
      activeTop="wallet"
      activeSide="wallet"
      user={user}
      onLogout={onLogout}
    >
      <main className="home-main hm lw">
        <div className="lw-layout">
          {/* 제목 + 주/월 보기 */}
          <div className="lw-head">
            <div className="lw-head-left">
              <h1 className="lw-title">가계부</h1>
              <div className="lw-tabs" role="tablist" aria-label="가계부 보기">
                {[
                  [false, "날짜"],
                  [true, "목록"],
                ].map(([isList, label]) => (
                  <button
                    key={label}
                    type="button"
                    role="tab"
                    aria-selected={listView === isList}
                    className={listView === isList ? "is-active" : ""}
                    onClick={() => setListView(isList)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
            <div className="lw-seg" role="group" aria-label="보기 방식">
              {[
                ["week", "주"],
                ["month", "월"],
              ].map(([mode, label]) => (
                <button
                  key={mode}
                  type="button"
                  className={viewMode === mode ? "is-active" : ""}
                  aria-pressed={viewMode === mode}
                  onClick={() => setViewMode(mode)}
                >
                  {viewMode === mode && <Icon name="check" size={13} strokeWidth={2.4} />}
                  {label}
                </button>
              ))}
            </div>
          </div>

          {listView ? (
            <div
              ref={listViewRef}
              className={`lw-listview ${listWide && detail ? "has-detail" : ""}`}
              role="tabpanel"
              aria-label="내역 목록"
            >
              {periodNav}
              <section className="hm-card lw-list-card" aria-label="수입·지출 내역">
                <div className="lw-list-toolbar">
                  <button
                    type="button"
                    className={`lw-filter-btn ${listFilterActive ? "is-active" : ""}`}
                    aria-haspopup="dialog"
                    onClick={() => setSheet("filter")}
                  >
                    필터
                    {listFilterActive && <span className="lw-filter-dot" aria-label="필터 적용 중" />}
                    <Icon name="chevronDown" size={14} />
                  </button>
                  <button
                    type="button"
                    className="hm-icon-btn lw-search-btn"
                    aria-label="내역 검색"
                    aria-haspopup="dialog"
                    onClick={() => setSheet("search")}
                  >
                    <SearchGlyph size={20} />
                  </button>
                </div>
                <p className="lw-list-summary">
                  <span>{listCount}건</span>
                  <span className="lw-amt-in">수입 +{formatWon(listIncome)}</span>
                  <span>지출 -{formatWon(listExpense)}</span>
                </p>
                {loading ? (
                  <p className="lw-cal-state">불러오는 중…</p>
                ) : error ? (
                  <p className="lw-cal-state lw-cal-state--error">{error}</p>
                ) : listGroups.length === 0 ? (
                  <div className="hm-empty">
                    <p>
                      {listFilterActive
                        ? "필터에 맞는 내역이 없어요."
                        : viewMode === "week"
                          ? "이 주에는 내역이 없어요."
                          : "이 달에는 내역이 없어요."}
                    </p>
                  </div>
                ) : (
                  listGroups.map((g) => {
                    const iso = toIsoDate(viewYear, viewMonth, g.day);
                    const weekday = new Intl.DateTimeFormat("ko-KR", { weekday: "long" }).format(
                      new Date(viewYear, viewMonth - 1, g.day),
                    );
                    return (
                      <div key={g.day} className="lw-list-group">
                        <div className="lw-list-group-head">
                          <span>
                            {g.day}일 {weekday}
                            {iso === todayIso && <small>오늘</small>}
                          </span>
                          <span className="lw-list-group-sums">
                            {g.income > 0 && <span className="lw-amt-in">+{formatWon(g.income)}</span>}
                            {g.expense > 0 && <span>-{formatWon(g.expense)}</span>}
                          </span>
                        </div>
                        <ul className="hm-tx-list">
                          {g.rows.map((row) => renderListRow(row, pickListRow, true))}
                        </ul>
                      </div>
                    );
                  })
                )}
              </section>
              {/* 넓은 화면 — 목록 오른쪽 패널(같은 줄을 다시 누르거나, 패널 바깥을 누르거나, Esc 로 접힌다) */}
              {listWide && detail && (
                <aside
                  ref={detailPanelRef}
                  className={`hm-card lw-detail-panel ${detail.closing ? "is-closing" : ""}`}
                  aria-label="내역 상세"
                >
                  <div className="lw-detail-panel-head">
                    <h2 className="hm-card-title">상세 내역</h2>
                  </div>
                  {renderDetail(detail.row)}
                </aside>
              )}
              {/* 좁은 화면(폰) — 아래에서 올라오는 시트 */}
              {!listWide && detail && !detail.closing && (
                <LedgerSheet
                  title="상세 내역"
                  onClose={() => closeDetail(false)}
                  showClose={false}
                  className="lw-sheet--detail"
                >
                  {renderDetail(detail.row)}
                </LedgerSheet>
              )}
              {sheet === "filter" && (
                <LedgerFilterSheet
                  value={listFilter}
                  instruments={instruments}
                  categoryOptions={categoryOptions}
                  incomeOptions={CATEGORIES_BY_TYPE.income}
                  onApply={(next) => {
                    setListFilter(next);
                    setSheet(null);
                  }}
                  onClose={closeSheet}
                />
              )}
              {sheet === "search" && (
                <LedgerSearchSheet
                  loadMonth={loadSearchMonth}
                  today={today}
                  renderRow={(row) => renderListRow(row, openSearchResult)}
                  onClose={closeSheet}
                />
              )}
            </div>
          ) : (
          <>
          {/* 왼쪽 단(PC) — 기간 이동 · 총 지출 · 달력. 폰에서는 단이 풀려 한 줄로 이어진다 */}
          <div className="lw-col lw-col--main">
            {periodNav}

            {/* ① 이번 달 총 지출 — 이 화면의 핵심 숫자 */}
            <section className="hm-card hm-hero" aria-labelledby="lw-hero-label">
              <div className="hm-hero-top">
                <p className="hm-hero-label" id="lw-hero-label">
                  {viewMonth}월 총 지출
                </p>
                {monthBudget > 0 ? (
                  <span className="lw-hero-budget">월 예산 {formatWon(monthBudget)}</span>
                ) : null}
              </div>
              {loading ? (
                <p className="hm-hero-value hm-skeleton" aria-busy="true">
                  <span className="hm-sr">불러오는 중</span>
                </p>
              ) : (
                <p className={`hm-hero-value ${budgetSignal === "RED" ? "is-over" : ""}`}>
                  {monthExpenseTotal.toLocaleString("ko-KR")}
                  <span className="hm-hero-unit">원</span>
                </p>
              )}
              {monthBudget > 0 && budgetSignal ? (
                <>
                  <div
                    className={`hm-meter hm-meter--${SIGNAL_META[budgetSignal].tone}`}
                    role="progressbar"
                    aria-label={`${viewMonth}월 예산 사용률`}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={Math.min(budgetPct, 100)}
                    aria-valuetext={`${budgetPct}% 사용, ${SIGNAL_META[budgetSignal].text}`}
                  >
                    <span style={{ width: `${Math.min(budgetPct, 100)}%` }} />
                  </div>
                  <p className="hm-hero-foot">
                    <span>
                      {monthBudget - monthExpenseTotal >= 0
                        ? <>예산에서 <strong>{formatWon(monthBudget - monthExpenseTotal)}</strong> 남았어요</>
                        : <>예산보다 <strong>{formatWon(monthExpenseTotal - monthBudget)}</strong> 더 썼어요</>}
                    </span>
                    <span className="lw-hero-right">
                      <SignalChip signal={budgetSignal} />
                      <span className="hm-hero-pct">예산의 {budgetPct}%</span>
                    </span>
                  </p>
                </>
              ) : plan === null ? (
                <p className="hm-hero-foot">예산을 불러오지 못했어요.</p>
              ) : plan !== undefined ? (
                <p className="hm-hero-foot">월수입을 입력하면 고정비·예비비를 뺀 예산과 비교해 드려요.</p>
              ) : null}
              <p className="lw-hero-sub">
                수입 {formatWon(monthIncomeTotal)} · 기록 {rows.length}건 · AI 판정 {aiJudgedCount}건
              </p>
            </section>

            {/* ② 달력 */}
            <section className="hm-card lw-cal" aria-label={viewMode === "week" ? "주간 달력" : "월간 달력"}>
              <div className="lw-cal-weekdays" aria-hidden>
                {WEEKDAYS.map((weekday, i) => (
                  <span key={weekday} className={i === 0 ? "is-sun" : i === 6 ? "is-sat" : ""}>
                    {weekday}
                  </span>
                ))}
              </div>
              <div className="lw-cal-grid">
                {viewMode === "month"
                  ? calendarCells.map((day, index) => {
                      if (day === null) {
                        return <span key={`empty-${index}`} className="lw-cell lw-cell--empty" aria-hidden />;
                      }
                      const dayIso = toIsoDate(viewYear, viewMonth, day);
                      return (
                        <CalendarCell
                          key={day}
                          day={day}
                          month={viewMonth}
                          weekday={(firstWeekday + day - 1) % 7}
                          rows={rowsByDay.get(day) ?? []}
                          signal={signalByDay.get(day)}
                          judged={isFinalJudgment(judgmentsByDate[dayIso])}
                          isToday={day === todayDay}
                          isSelected={selectedDay === day}
                          isFuture={dayIso > todayIso}
                          onSelect={() => selectDay(day)}
                        />
                      );
                    })
                  : weekDates.map((d) => {
                      const isCurrentMonth = d.getMonth() + 1 === viewMonth && d.getFullYear() === viewYear;
                      const day = d.getDate();
                      if (!isCurrentMonth) {
                        return (
                          <span key={d.toISOString()} className="lw-cell lw-cell--other" aria-hidden>
                            <span className="lw-cell-day">{day}</span>
                          </span>
                        );
                      }
                      const dayIso = toIsoDate(viewYear, viewMonth, day);
                      return (
                        <CalendarCell
                          key={d.toISOString()}
                          day={day}
                          month={viewMonth}
                          weekday={d.getDay()}
                          rows={rowsByDay.get(day) ?? []}
                          signal={signalByDay.get(day)}
                          judged={isFinalJudgment(judgmentsByDate[dayIso])}
                          isToday={day === todayDay}
                          isSelected={selectedDay === day}
                          isFuture={dayIso > todayIso}
                          onSelect={() => selectDay(day)}
                        />
                      );
                    })}
              </div>
              {loading && <p className="lw-cal-state">불러오는 중…</p>}
              {error && !loading && <p className="lw-cal-state lw-cal-state--error">{error}</p>}
              <ul className="lw-legend" aria-label="판정 범례">
                {["GREEN", "GRAY", "RED"].map((s) => (
                  <li key={s} className={`lw-legend-item lw-legend-item--${SIGNAL_META[s].tone}`}>
                    <Icon name={SIGNAL_META[s].icon} size={13} strokeWidth={2.4} />
                    {SIGNAL_META[s].text}
                  </li>
                ))}
              </ul>
            </section>
          </div>

          {/* 오른쪽 단(PC) — 선택한 날 · 지출 내역 · 카테고리 */}
          <div className="lw-col lw-col--side">
            {/* ③ 선택한 날 — 그날 내역 · 판정 보상 · 지출 수정 / 판정 */}
            <section className="hm-card lw-day" ref={dayCardRef} aria-labelledby="lw-day-title">
              <div className="hm-card-head">
                <h2 className="hm-card-title" id="lw-day-title">
                  {selectedDay ? dayLabel(viewYear, viewMonth, selectedDay) : "날짜를 선택하세요"}
                  {selectedIsToday && <small>오늘</small>}
                </h2>
                {selectedDaySignal && (
                  <SignalChip
                    signal={selectedDaySignal}
                    label={
                      isFinalJudgment(selectedJudgment)
                        ? SIGNAL_META[selectedDaySignal].text
                        : `${SIGNAL_META[selectedDaySignal].text} (예상)`
                    }
                  />
                )}
              </div>

              {selectedDayRows.length === 0 ? (
                <div className="hm-empty">
                  <p>이 날짜에는 등록된 내역이 없어요.</p>
                </div>
              ) : (
                <ul className="lw-day-list">
                  {selectedDayRows.map((row) => {
                    const open = selectedId === row.id;
                    return (
                      <li key={row.id} className={open ? "is-open" : ""}>
                        <button
                          type="button"
                          className="lw-day-row"
                          aria-expanded={open}
                          onClick={() => setSelectedId(open ? null : row.id)}
                        >
                          <span className="hm-tx-icon">
                            <Icon name={iconFor(row)} size={18} />
                          </span>
                          <span className="hm-tx-mid">
                            <span className="hm-tx-name">{row.name}</span>
                            <span className="hm-tx-cat">{row.cat}</span>
                          </span>
                          <span className="hm-tx-right">
                            <span className={`hm-tx-amount ${row.type === "INCOME" ? "lw-amt-in" : ""}`}>
                              {row.type === "INCOME" ? "+" : "-"}
                              {row.amount}
                            </span>
                            {row.aiStatus && <SignalChip signal={row.signal} />}
                          </span>
                          <Icon name="chevronDown" size={16} className="lw-day-caret" />
                        </button>
                        {open && (
                          <div className="lw-detail" aria-live="polite">
                            <dl className="lw-detail-grid">
                              <div>
                                <dt>구분</dt>
                                <dd>{row.type === "INCOME" ? "수입" : "지출"}</dd>
                              </div>
                              <div>
                                <dt>결제수단</dt>
                                <dd>{row.payment || "—"}</dd>
                              </div>
                              <div>
                                <dt>날짜</dt>
                                <dd>{row.date}</dd>
                              </div>
                              <div>
                                <dt>카테고리</dt>
                                <dd>{row.cat}</dd>
                              </div>
                            </dl>
                            {row.memo && (
                              <p className="lw-detail-note">
                                <span>메모</span>
                                {row.memo}
                              </p>
                            )}
                            {row.reason && (
                              <p className="lw-detail-note">
                                <span>소비 사유</span>
                                {row.reason}
                              </p>
                            )}
                            {row.aiStatus && (
                              <p className="lw-detail-ai">
                                AI 판정 <SignalChip signal={row.signal} />
                              </p>
                            )}
                          </div>
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}

              {/* 판정 보상 */}
              {selectedDateIso && !selectedDateIsFuture && (
                <div className="lw-reward" aria-label="선택한 날짜의 판정 보상">
                  <div className="lw-reward-head">
                    <Icon name="paw" size={16} />
                    <strong>{rewardStatus}</strong>
                  </div>
                  {judgmentLoading ? (
                    <p>보상 정보를 불러오는 중이에요.</p>
                  ) : judgmentError ? (
                    <p className="lw-reward-error">{judgmentError}</p>
                  ) : selectedJudgment && !isFinalJudgment(selectedJudgment) ? (
                    <p>
                      {selectedDateIso < todayIso
                        ? "자정이 지나 1차 판정 내용으로 확정돼요. 잠시 뒤 다시 확인해 주세요."
                        : "예외 지출 사유를 답하거나 건너뛰면 최종 판정이 나요. 보상은 오늘 자정에 들어와요."}
                    </p>
                  ) : selectedJudgment ? (
                    <ul className="lw-reward-values">
                      <li className="lw-reward-coin">
                        <CoinIcon size={16} />
                        {selectedJudgment.rewarded ? "받은 코인" : "자정에 받을 코인"}
                        <strong>+{Number(selectedJudgment.coinReward || 0).toLocaleString("ko-KR")}</strong>
                      </li>
                      {STAT_REWARDS.filter(({ key }) => Number(selectedJudgment.statRewards?.[key] ?? 0) > 0).map(
                        ({ key, label, color }) => (
                          <li key={key} className="lw-reward-stat" style={{ "--stat-color": color }}>
                            {label} <strong>+{selectedJudgment.statRewards[key]}</strong>
                          </li>
                        ),
                      )}
                    </ul>
                  ) : (
                    <p>
                      {selectedDateIso >= todayIso
                        ? "판정하지 않아도 지출을 적은 날은 자정에 신호등 판정이 내려지고 보상이 들어와요."
                        : "이 날짜는 판정 기록이 없어 지급된 보상이 없어요."}
                    </p>
                  )}
                </div>
              )}

              {selectedDay && !selectedDateIsFuture && (
                <div className={`lw-day-actions ${showJudgeButton ? "" : "is-single"}`}>
                  <button type="button" className="hm-btn lw-btn-soft" onClick={handleAddOrEditExpense}>
                    <Icon name={selectedDayExpenses.length > 0 ? "pencil" : "plus"} size={16} strokeWidth={2.2} />
                    {selectedDayExpenses.length > 0 ? "지출 수정" : "지출 추가"}
                  </button>
                  {showJudgeButton && (
                    <button
                      type="button"
                      className="hm-btn hm-btn--dark"
                      disabled={!isAiActive || (isAdmin && !demoDate)}
                      onClick={() => navigate(isAdmin ? `/wallet/analysis?date=${demoDate}` : "/wallet/analysis")}
                    >
                      {judgeLabel}
                      {isAiActive && <Icon name="arrowRight" size={16} strokeWidth={2.2} />}
                    </button>
                  )}
                </div>
              )}
              {isAdmin && (
                <label className="lw-admin-date">
                  시연할 날짜 (관리자)
                  <input
                    type="date"
                    value={demoDate}
                    onChange={(e) => setDemoDate(e.target.value)}
                    aria-label="관리자 판정 날짜"
                  />
                </label>
              )}
              {showJudgeButton && pendingCount != null && pendingCount > 0 && (
                <p className="lw-footnote">오늘 답하지 않은 질문 {pendingCount}건</p>
              )}
            </section>

            {/* ④ 지출 내역 — 표 대신 목록(폰에서 옆으로 넘치지 않게) */}
            <section className="hm-card" aria-labelledby="lw-history-title">
              <div className="hm-card-head">
                <h2 className="hm-card-title" id="lw-history-title">
                  {viewMode === "week" ? "주간 지출 내역" : "월간 지출 내역"}
                  <small>{visibleExpenseRows.length}건</small>
                </h2>
                <span className="lw-total">총 {formatWon(visibleExpenseTotal)}</span>
              </div>
              {!loading && visibleExpenseRows.length === 0 ? (
                <div className="hm-empty">
                  <p>{viewMode === "week" ? "이 기간에는 지출이 없어요." : "이 달에는 등록된 지출이 없어요."}</p>
                </div>
              ) : (
                <ul className="hm-tx-list">
                  {pagedExpenseRows.map((row) => (
                    <li key={row.id}>
                      <button type="button" className="hm-tx" onClick={() => selectRow(row)}>
                        <span className="hm-tx-icon">
                          <Icon name={iconFor(row)} size={18} />
                        </span>
                        <span className="hm-tx-mid">
                          <span className="hm-tx-name">{row.name}</span>
                          <span className="hm-tx-cat">
                            {viewMonth}.{pad2(row.day)} · {row.cat}
                          </span>
                        </span>
                        <span className="hm-tx-right">
                          <span className="hm-tx-amount">-{row.amount}</span>
                          {row.aiStatus && <SignalChip signal={row.signal} />}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {historyTotalPages > 1 && (
                <div className="lw-pager">
                  <button
                    type="button"
                    className="hm-icon-btn"
                    aria-label="이전 페이지"
                    disabled={safeHistoryPage === 1}
                    onClick={() => setHistoryPage((p) => Math.max(1, p - 1))}
                  >
                    <Icon name="chevronLeft" size={16} />
                  </button>
                  <span>
                    {safeHistoryPage} / {historyTotalPages}
                  </span>
                  <button
                    type="button"
                    className="hm-icon-btn"
                    aria-label="다음 페이지"
                    disabled={safeHistoryPage === historyTotalPages}
                    onClick={() => setHistoryPage((p) => Math.min(historyTotalPages, p + 1))}
                  >
                    <Icon name="chevronRight" size={16} />
                  </button>
                </div>
              )}
            </section>

            {/* ⑤ 카테고리별 지출 — 리포트로 이어진다 */}
            <section className="hm-card" aria-labelledby="lw-cat-title">
              <div className="hm-card-head">
                <h2 className="hm-card-title" id="lw-cat-title">
                  카테고리별 지출
                </h2>
                <button type="button" className="hm-text-link" onClick={() => navigate("/report")}>
                  리포트 <Icon name="chevronRight" size={14} />
                </button>
              </div>
              {categoryBreakdown.length > 0 ? (
                <ul className="lw-cats">
                  {categoryBreakdown.map((c) => (
                    <li key={c.label} style={{ "--cat-color": c.color }}>
                      <span className="lw-cat-label">{c.label}</span>
                      <span className="lw-cat-track">
                        <span style={{ width: `${c.pct}%` }} />
                      </span>
                      <span className="lw-cat-pct">{c.pct}%</span>
                      <span className="lw-cat-amt">{c.amount.toLocaleString("ko-KR")}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className="hm-empty">
                  <p>이번 달 지출이 없어요.</p>
                </div>
              )}
            </section>
          </div>
          </>
          )}
        </div>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default WalletPage;
