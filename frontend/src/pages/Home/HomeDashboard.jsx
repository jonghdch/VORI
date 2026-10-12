import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import AppRightSidebar from "../../components/AppRightSidebar";
import Icon from "../../components/Icon";
import { getHomeSummary } from "../../api/home";
import { getMonthlyLedger } from "../../api/ledger";
import { getActivePet } from "../../api/pet";
import { getMe, PET_CHANGED_EVENT } from "../../api/user";
import { getUnreadMonthlyReport } from "../../api/monthlyReports";
import { getSpendingPlan } from "../../api/spendingPlan";
import { getTodayJudgment } from "../../api/dailyJudgment";
import { petVisual, petDisplayName, levelProgressPct } from "../../components/petVisual";
import { AI_ACTIVE_FROM_HOUR, canUseAiJudge } from "../../config";
import { NO_RECORD_LINE, SPENDING_LINES, monthlyReportLine, petTmiLines } from "../../components/petLines";
import { SIGNAL_STATUS } from "../../signalLabels";
import SignalChip, { SIGNAL_META, categoryIcon, signalForUsage } from "../../components/SignalChip";
// HomeDashboard.css 는 가계부·리포트 화면도 .home-tx-* 등을 가져다 쓰므로 그대로 두고,
// 새 홈 배치는 HomeScreen.css 의 .hm-* 로만 그린다.
import "./HomeDashboard.css";
import "./HomeScreen.css";

// 스탯 4종 — 값은 키우는 펫 본인의 스탯(PetResponse.stat*). 펫 화면과 같은 값이다.
// 홈 요약(summary.stats)은 지출에 쌓인 변동만 합산해 출석 아이템 같은 변화가 빠지므로 쓰지 않는다.
// group 은 그 스탯을 키우는 지출 그룹(판정 화면 STAT_LABELS 와 같은 이름).
const STAT_META = [
  { key: "statEnergy", label: "에너지", group: "식비", color: "#7fb069" },
  { key: "statCharm", label: "매력", group: "쇼핑·뷰티", color: "#e07a7a" },
  { key: "statIq", label: "지능", group: "문화·여가", color: "#6b9bd1" },
  { key: "statEndurance", label: "지구력", group: "생활·고정비", color: "#c9913f" },
];

const num = (n) => Math.round(n ?? 0).toLocaleString("ko-KR");
const won = (n) => `${num(n)}원`;
const pad2 = (n) => String(n).padStart(2, "0");
/** 20 → "오후 8시", 0 → "자정" — 판정이 열리는 시각 안내용 */
const hourLabel = (h) =>
  h === 0 ? "자정" : h < 12 ? `오전 ${h}시` : h === 12 ? "낮 12시" : `오후 ${h - 12}시`;

// 월간 리포트 버튼을 띄우는 기간 — 매달 마지막 날 12시(월간 정산 시각)부터 7일.
const REPORT_WINDOW_DAYS = 7;

/** 지금이 리포트 기간이면 그 리포트의 달("YYYY-MM"), 아니면 null. 기간은 다음 달 초까지 걸친다. */
function reportWindowMonth(now = new Date()) {
  for (const offset of [0, -1]) {
    const start = new Date(now.getFullYear(), now.getMonth() + offset + 1, 0, 12);
    const end = new Date(start);
    end.setDate(end.getDate() + REPORT_WINDOW_DAYS);
    if (now >= start && now < end) {
      return `${start.getFullYear()}-${pad2(start.getMonth() + 1)}`;
    }
  }
  return null;
}

function HomeDashboard({ user, onNavigate, onLogout }) {
  const navigate = useNavigate();
  const today = new Date();
  const todayIso = `${today.getFullYear()}-${pad2(today.getMonth() + 1)}-${pad2(today.getDate())}`;
  const yearMonth = todayIso.slice(0, 7);

  // ── 데이터 ─────────────────────────────────────────────
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let alive = true;
    getHomeSummary()
      .then((res) => alive && setSummary(res))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, []);

  // 키우는 펫 + 안 본 월간 리포트(펫 말풍선). 실패해도 홈은 떠야 하므로 조용히 넘어간다.
  const [activePet, setActivePet] = useState(null);
  // 조회가 끝나기 전에는 "펫 없음" 안내를 띄우지 않는다 — 잠깐 떴다 사라지는 깜빡임 방지
  const [petLoaded, setPetLoaded] = useState(false);
  const [unreadReport, setUnreadReport] = useState(null);
  useEffect(() => {
    let alive = true;
    const loadPet = () =>
      getActivePet()
        .then((p) => {
          if (!alive) return;
          setActivePet(p);
          setPetLoaded(true);
        })
        .catch(() => {});
    loadPet();
    window.addEventListener(PET_CHANGED_EVENT, loadPet);
    getUnreadMonthlyReport()
      .then((r) => alive && setUnreadReport(r))
      .catch(() => {});
    return () => {
      alive = false;
      window.removeEventListener(PET_CHANGED_EVENT, loadPet);
    };
  }, []);

  // 이번 달 가계부 — 오늘 지출 목록(최근 N건이 아니라 오늘 전부)을 여기서 고른다. null = 로딩 중.
  const [ledgerRows, setLedgerRows] = useState(null);
  useEffect(() => {
    let alive = true;
    getMonthlyLedger(yearMonth)
      .then((rows) => alive && setLedgerRows(Array.isArray(rows) ? rows : []))
      .catch(() => alive && setLedgerRows([]));
    return () => {
      alive = false;
    };
  }, [yearMonth]);

  // 이번 달 스탯 그룹별 예산(합계 = 이번 달 쓸 수 있는 돈). undefined = 로딩 중, null = 실패.
  const [plan, setPlan] = useState(undefined);
  useEffect(() => {
    let alive = true;
    getSpendingPlan(yearMonth)
      .then((p) => alive && setPlan(p ?? null))
      .catch(() => alive && setPlan(null));
    return () => {
      alive = false;
    };
  }, [yearMonth]);

  // 오늘 판정 행(없으면 null) — 판정 카드 상태와, 판정 뒤에는 서버가 계산한 오늘 쓸 수 있는 돈에 쓴다.
  const [judgment, setJudgment] = useState(undefined);
  useEffect(() => {
    let alive = true;
    getTodayJudgment()
      .then((j) => alive && setJudgment(j ?? null))
      .catch(() => alive && setJudgment(null));
    return () => {
      alive = false;
    };
  }, []);

  // 판정이 열렸는지 — 관리자는 언제나, 일반 계정은 AI_ACTIVE_FROM_HOUR 부터. 1분마다 다시 본다.
  const [me, setMe] = useState(null);
  const [judgeOpen, setJudgeOpen] = useState(() => canUseAiJudge(user));
  useEffect(() => {
    let alive = true;
    getMe()
      .then((m) => alive && setMe(m))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  useEffect(() => {
    const check = () => setJudgeOpen(canUseAiJudge(me ?? user));
    check();
    const timer = setInterval(check, 60_000);
    return () => clearInterval(timer);
  }, [me, user]);

  // ── 오늘 남은 돈 ───────────────────────────────────────
  const todaySpent = summary?.spending?.today ?? 0;
  const monthSpent = summary?.spending?.thisMonth ?? 0;
  const monthBudget = Object.values(plan?.budgets || {}).reduce((s, v) => s + Number(v || 0), 0);

  // 오늘 쓸 수 있는 돈.
  //  - 오늘 판정을 받았으면 서버가 그룹별로 계산한 값(availableToday 합)을 그대로 쓴다.
  //  - 판정 전에는 서버 값이 없어서 "이번 달 남은 예산 ÷ 오늘 포함 남은 날"로 어림한다.
  //    서버 봉투는 전날 남긴 돈의 절반만 넘겨 오므로 판정 뒤 숫자와 조금 다를 수 있다 → 화면에 "예상"으로 표시.
  const allowanceInfo = useMemo(() => {
    const groups = judgment?.groupJudgments ? Object.values(judgment.groupJudgments) : [];
    if (groups.length > 0) {
      return {
        value: groups.reduce((s, g) => s + Number(g?.availableToday || 0), 0),
        estimated: false,
      };
    }
    if (monthBudget <= 0) return null;
    const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate();
    const daysLeft = lastDay - today.getDate() + 1;
    const spentBeforeToday = Math.max(monthSpent - todaySpent, 0);
    return {
      value: Math.max(Math.floor((monthBudget - spentBeforeToday) / daysLeft), 0),
      estimated: true,
    };
    // today 는 렌더마다 새 객체라 의존성에서 뺀다 — 날짜 값은 todayIso 로 본다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [judgment, monthBudget, monthSpent, todaySpent, todayIso]);

  const allowance = allowanceInfo?.value ?? 0;
  const remaining = allowance - todaySpent;
  const usagePct = allowance > 0 ? Math.round((todaySpent / allowance) * 100) : todaySpent > 0 ? 100 : 0;
  const todaySignal = signalForUsage(todaySpent, allowance);
  const heroLoading = loading || plan === undefined || judgment === undefined;

  // ── 오늘 지출 목록 ────────────────────────────────────
  const todayRows = useMemo(
    () => (ledgerRows ?? []).filter((r) => r.type === "EXPENSE" && r.date === todayIso),
    [ledgerRows, todayIso],
  );

  // ── 펫 ─────────────────────────────────────────────
  const equippedTitle = activePet?.equippedTitle ?? null;
  const petLevel = activePet?.level ?? 1;
  const petExp = levelProgressPct(activePet);
  const petImage = activePet ? petVisual(activePet.appearanceKey, activePet.stage).image : null;
  const statScale = Math.max(100, ...STAT_META.map((m) => activePet?.[m.key] ?? 0));

  // 말풍선 ③ 에 쓸 무작위 값 — 화면을 열 때 한 번만 정해 다시 그려져도 대사가 바뀌지 않게
  const [bubbleSeed] = useState(() => Math.random());
  const bubble = useMemo(() => {
    if (loading) return { text: "오늘 소비를 살펴보고 있어요…" };
    if (todaySpent <= 0) return { text: NO_RECORD_LINE };
    if (unreadReport) {
      const month = Number(unreadReport.yearMonth.slice(5));
      return { text: monthlyReportLine(month), link: `/report?month=${unreadReport.yearMonth}` };
    }
    const lines = [
      ...SPENDING_LINES.map((l) => l.replace("{today}", won(todaySpent))),
      ...petTmiLines(activePet),
    ];
    return { text: lines[Math.floor(bubbleSeed * lines.length)] };
  }, [loading, todaySpent, unreadReport, activePet, bubbleSeed]);

  const reportMonth = reportWindowMonth(today);
  const [statHelpOpen, setStatHelpOpen] = useState(false);

  const dateLabel = new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(today);

  // ── 판정 카드 상태 ─────────────────────────────────────
  const judgeCard = (() => {
    if (judgment?.status === "FINALIZED") {
      return {
        signal: judgment.signal,
        desc: judgment.rewarded ? "오늘 보상을 받았어요." : "보상은 오늘 자정에 들어와요.",
        action: "결과 보기",
        to: "/wallet/analysis",
      };
    }
    if (judgment?.status === "PENDING") {
      return {
        signal: judgment.signal,
        signalLabel: `1차 ${SIGNAL_STATUS[judgment.signal] ?? ""}`,
        desc: "확인이 필요한 지출이 답을 기다리고 있어요.",
        action: "이어서 하기",
        to: "/wallet/analysis",
      };
    }
    if (!judgeOpen) {
      return {
        desc: `${hourLabel(AI_ACTIVE_FROM_HOUR)}부터 오늘 지출을 판정받을 수 있어요.`,
        action: `${hourLabel(AI_ACTIVE_FROM_HOUR)}부터`,
        disabled: true,
      };
    }
    return {
      desc: "오늘 지출을 판정받고 코인을 모아요.",
      action: "판정 받기",
      to: "/wallet/analysis",
    };
  })();

  return (
    <AppShell activeTop="home" activeSide="home" onNavigate={onNavigate} onLogout={onLogout}>
      <main className="home-main hm hm-home">
        {/* 폰: 한 줄(오늘 남은 돈 → 펫·판정 → 스탯 → 오늘 지출). PC 본문이 넓으면 두 단:
            왼쪽 = 돈(오늘 남은 돈 · 오늘 지출), 오른쪽 = 펫(펫·판정 · 리포트 · 스탯). 폰 순서는 CSS order 로 맞춘다. */}
        <div className="hm-layout">
          <div className="hm-col hm-col--main">
            {/* ① 오늘 남은 돈 — 이 화면의 핵심 숫자 */}
            <section className="hm-card hm-hero" aria-labelledby="hm-hero-label">
              <div className="hm-hero-top">
                <p className="hm-hero-label" id="hm-hero-label">
                  {dateLabel} · {allowanceInfo && remaining < 0 ? "오늘 넘은 돈" : "오늘 남은 돈"}
                </p>
                {!heroLoading && allowanceInfo && <SignalChip signal={todaySignal} />}
              </div>

              {heroLoading ? (
                <p className="hm-hero-value hm-skeleton" aria-busy="true">
                  <span className="hm-sr">불러오는 중</span>
                </p>
              ) : allowanceInfo ? (
                <>
                  <p className={`hm-hero-value ${remaining < 0 ? "is-over" : ""}`}>
                    {num(Math.abs(remaining))}
                    <span className="hm-hero-unit">원</span>
                  </p>
                  <div
                    className={`hm-meter hm-meter--${SIGNAL_META[todaySignal].tone}`}
                    role="progressbar"
                    aria-label="오늘 쓸 수 있는 돈 사용률"
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={Math.min(usagePct, 100)}
                    aria-valuetext={`${usagePct}% 사용, ${SIGNAL_META[todaySignal].text}`}
                  >
                    <span style={{ width: `${Math.min(usagePct, 100)}%` }} />
                  </div>
                  <p className="hm-hero-foot">
                    <span>
                      오늘 쓸 수 있는 {won(allowance)}
                      {allowanceInfo.estimated && <em className="hm-hero-est">(예상)</em>} 중{" "}
                      <strong>{won(todaySpent)}</strong> 썼어요
                    </span>
                    <span className="hm-hero-pct">{usagePct}%</span>
                  </p>
                </>
              ) : (
                // 예산을 계산할 수입 정보가 없을 때 — 오늘 쓴 돈을 크게 보여 주고 설정으로 안내
                <>
                  <p className="hm-hero-value">
                    {num(todaySpent)}
                    <span className="hm-hero-unit">원</span>
                  </p>
                  <p className="hm-hero-foot">
                    <span>오늘 쓴 돈이에요. 월수입을 입력하면 오늘 남은 돈을 알려 드려요.</span>
                  </p>
                  <button type="button" className="hm-text-link" onClick={() => navigate("/settings/profile")}>
                    월수입 입력하기 <Icon name="chevronRight" size={14} />
                  </button>
                </>
              )}
            </section>

            {/* ④ 오늘 지출 — 한 건씩 */}
            <section className="hm-card hm-today" aria-labelledby="hm-today-title">
              <div className="hm-card-head">
                <h2 className="hm-card-title" id="hm-today-title">
                  오늘 지출 {ledgerRows && <small>{todayRows.length}건</small>}
                </h2>
                <button type="button" className="hm-text-link" onClick={() => navigate("/wallet")}>
                  전체 보기 <Icon name="chevronRight" size={14} />
                </button>
              </div>
              {ledgerRows === null ? (
                <p className="hm-empty">불러오는 중…</p>
              ) : todayRows.length === 0 ? (
                <div className="hm-empty">
                  <p>오늘은 아직 기록한 지출이 없어요.</p>
                </div>
              ) : (
                <ul className="hm-tx-list">
                  {todayRows.map((row) => (
                    <li key={`${row.type}-${row.id}`}>
                      <button
                        type="button"
                        className="hm-tx"
                        onClick={() => navigate(`/wallet?date=${todayIso}`)}
                      >
                        <span className="hm-tx-icon">
                          <Icon name={categoryIcon(row.category)} size={18} />
                        </span>
                        <span className="hm-tx-mid">
                          <span className="hm-tx-name">{row.item || "지출"}</span>
                          <span className="hm-tx-cat">{row.category || "미분류"}</span>
                        </span>
                        <span className="hm-tx-right">
                          <span className="hm-tx-amount">-{won(row.amount)}</span>
                          {row.signal && <SignalChip signal={row.signal} />}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>

          <div className="hm-col hm-col--side">
            {/* ② 펫 · 오늘 판정 — 나란히 두 칸 */}
            <div className="hm-duo">
              <section className="hm-card hm-pet" aria-label="키우는 펫">
                {petLoaded && !activePet ? (
                  <div className="hm-pet-empty">
                    <span className="hm-pet-empty-icon">
                      <Icon name="paw" size={28} />
                    </span>
                    <strong>아직 키우는 펫이 없어요</strong>
                    <button type="button" className="hm-btn hm-btn--dark hm-btn--sm" onClick={() => navigate("/shop")}>
                      상점 가기
                    </button>
                  </div>
                ) : (
                  <>
                    {bubble.link ? (
                      <button type="button" className="hm-bubble hm-bubble--link" onClick={() => navigate(bubble.link)}>
                        {bubble.text}
                      </button>
                    ) : (
                      <p className="hm-bubble">{bubble.text}</p>
                    )}
                    <button
                      type="button"
                      className="hm-pet-art"
                      onClick={() => navigate("/myroom")}
                      aria-label={`${activePet ? petDisplayName(activePet) : "펫"} — 마이룸 가기`}
                    >
                      {/* 펫을 불러오기 전에는 비워 둔다 — 기본 강아지가 잠깐 보였다 바뀌지 않게 */}
                      {activePet &&
                        (petImage ? (
                          <img src={petImage} alt="" className="hm-pet-img" />
                        ) : (
                          <Icon name="paw" size={48} className="hm-pet-fallback" />
                        ))}
                    </button>
                    <div className="hm-pet-name">
                      <strong>{activePet ? petDisplayName(activePet) : " "}</strong>
                      <span className="hm-pet-lv">Lv.{petLevel}</span>
                      <button
                        type="button"
                        className="hm-pet-title"
                        onClick={() => navigate("/dex?tab=titles")}
                        aria-label={`장착 칭호 ${equippedTitle?.name ?? "없음"} — 칭호 도감 열기`}
                      >
                        {equippedTitle?.name ?? "칭호 없음"}
                      </button>
                    </div>
                    <div className="hm-pet-exp">
                      <span className="hm-pet-exp-label">성장도</span>
                      <span
                        className="hm-pet-exp-track"
                        role="progressbar"
                        aria-label="다음 레벨까지"
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={petExp}
                      >
                        <span style={{ width: `${petExp}%` }} />
                      </span>
                      <span className="hm-pet-exp-value">
                        {activePet?.levelExpNeeded ? `${activePet.levelExp}/${activePet.levelExpNeeded}` : "MAX"}
                      </span>
                    </div>
                  </>
                )}
              </section>

              <section className="hm-card hm-judge" aria-labelledby="hm-judge-title">
                <span className="hm-judge-icon">
                  <Icon name="clock" size={20} />
                </span>
                <h2 className="hm-judge-title" id="hm-judge-title">
                  오늘 판정
                </h2>
                {judgeCard.signal && (
                  <SignalChip signal={judgeCard.signal} size="lg" label={judgeCard.signalLabel} />
                )}
                <p className="hm-judge-desc">{judgeCard.desc}</p>
                <button
                  type="button"
                  className="hm-btn hm-btn--dark hm-btn--block"
                  disabled={judgeCard.disabled}
                  onClick={() => judgeCard.to && navigate(judgeCard.to)}
                >
                  {judgeCard.action}
                  {!judgeCard.disabled && <Icon name="chevronRight" size={16} strokeWidth={2.2} />}
                </button>
              </section>
            </div>

            {/* 월말 정산 뒤 일주일 — 그 달 리포트로 가는 띠 */}
            {reportMonth && (
              <button
                type="button"
                className="hm-card hm-banner"
                onClick={() => navigate(`/report?month=${reportMonth}`)}
              >
                <Icon name="report" size={18} />
                <span>{Number(reportMonth.slice(5))}월 소비 리포트가 나왔어요</span>
                <Icon name="chevronRight" size={16} className="hm-banner-arrow" />
              </button>
            )}

            {/* ③ 반려 스탯 분석 */}
            {activePet && (
              <section className="hm-card hm-stats" aria-labelledby="hm-stats-title">
                <div className="hm-card-head">
                  <h2 className="hm-card-title" id="hm-stats-title">
                    반려 스탯 분석 <small>지출 습관 반영</small>
                  </h2>
                  <button
                    type="button"
                    className="hm-icon-btn"
                    aria-label="스탯 설명"
                    aria-expanded={statHelpOpen}
                    onClick={() => setStatHelpOpen((v) => !v)}
                  >
                    <Icon name="info" size={18} />
                  </button>
                </div>
                {statHelpOpen && (
                  <p className="hm-help">
                    지출 그룹마다 하루 예산보다 아낀 1,000원마다 그 스탯이 1씩 자라요. 아래 글자가 그 스탯을 키우는 지출 그룹이에요.
                  </p>
                )}
                <ul className="hm-stat-grid">
                  {STAT_META.map((m) => {
                    const value = activePet?.[m.key] ?? 0;
                    return (
                      <li key={m.key} className="hm-stat" style={{ "--stat-color": m.color }}>
                        <span className="hm-stat-label">{m.label}</span>
                        <strong className="hm-stat-value">{num(value)}</strong>
                        <span className="hm-stat-track" aria-hidden>
                          <span style={{ width: `${(Math.max(value, 0) / statScale) * 100}%` }} />
                        </span>
                        <span className="hm-stat-group">{m.group}</span>
                      </li>
                    );
                  })}
                </ul>
              </section>
            )}
          </div>

          <p className="hm-footnote">
            {user?.role === "ADMIN"
              ? "관리자는 언제든지 소비 판정을 확인할 수 있어요"
              : `매일 ${hourLabel(AI_ACTIVE_FROM_HOUR)}에 보리가 소비 검사를 시작해요`}
          </p>
        </div>

        {/* 기록 버튼 — 한 건씩 바로 적는다 */}
        <button type="button" className="hm-fab" onClick={() => navigate("/wallet/new")}>
          <Icon name="plus" size={18} strokeWidth={2.4} />
          기록
        </button>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default HomeDashboard;
