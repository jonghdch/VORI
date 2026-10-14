import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Icon from "../../components/Icon";
import { getSpendingPlan } from "../../api/spendingPlan";
import { getTodayJudgment } from "../../api/dailyJudgment";
import SignalChip, { SIGNAL_META, signalForUsage } from "../../components/SignalChip";
// 카드 모양은 리디자인 홈과 같은 HomeScreen.css(.hm-*)를 쓴다. 바깥 .hm.hm-home 이 색 변수·톤을 깐다.
import "./HomeScreen.css";

const num = (n) => Math.round(n ?? 0).toLocaleString("ko-KR");
const won = (n) => `${num(n)}원`;
const pad2 = (n) => String(n).padStart(2, "0");

/**
 * 오늘 남은 돈 카드 — 오늘 쓸 수 있는 돈, 쓴 돈, 사용률, 판정 칩.
 * summary/loading 은 홈이 이미 받아 둔 getHomeSummary 결과를 그대로 받는다.
 */
function TodayAllowanceCard({ summary, loading }) {
  const navigate = useNavigate();
  const today = new Date();
  const todayIso = `${today.getFullYear()}-${pad2(today.getMonth() + 1)}-${pad2(today.getDate())}`;
  const yearMonth = todayIso.slice(0, 7);

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

  // 오늘 판정 행(없으면 null) — 판정 뒤에는 서버가 계산한 오늘 쓸 수 있는 돈을 쓴다.
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

  const todaySpent = summary?.spending?.today ?? 0;
  const monthSpent = summary?.spending?.thisMonth ?? 0;
  const monthBudget = Object.values(plan?.budgets || {}).reduce((s, v) => s + Number(v || 0), 0);

  // 오늘 쓸 수 있는 돈.
  //  - 오늘 판정을 받았으면 서버가 그룹별로 계산한 값(availableToday 합)을 그대로 쓴다.
  //  - 판정 전에는 서버 값이 없어서 "이번 달 남은 예산 ÷ 오늘 포함 남은 날"로 어림한다 → 화면에 "예상"으로 표시.
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
  // 판정 전(또는 사유를 기다리는 1차 판정)의 칩은 예상이다 — 실제 판정은 전날 남긴 돈까지 넣어 계산해 결과가 바뀔 수 있다.
  const todaySignalFinal = Boolean(judgment) && judgment.status !== "PENDING";
  const todaySignalText = `${SIGNAL_META[todaySignal].text}${todaySignalFinal ? "" : " 예상"}`;
  const heroLoading = loading || plan === undefined || judgment === undefined;

  const dateLabel = new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(today);

  return (
    <div className="hm hm-home home-allowance">
      <section className="hm-card hm-hero" aria-labelledby="hm-hero-label">
        <div className="hm-hero-top">
          <p className="hm-hero-label" id="hm-hero-label">
            {dateLabel} · {allowanceInfo && remaining < 0 ? "오늘 넘은 돈" : "오늘 남은 돈"}
          </p>
          {!heroLoading && allowanceInfo && (
            <span className="hm-hero-signal">
              {!todaySignalFinal && <span className="hm-hero-est-tag">예상 결과</span>}
              <SignalChip signal={todaySignal} />
            </span>
          )}
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
              aria-valuetext={`${usagePct}% 사용, ${todaySignalText}`}
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
    </div>
  );
}

export default TodayAllowanceCard;
