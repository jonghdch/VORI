import { useState } from "react";
import CoinIcon from "../../components/CoinIcon";

const LABELS = { GREEN: "초록 · 합리적", GRAY: "노랑 · 보통", RED: "빨강 · 과소비" };
const STAT_LABELS = { ENERGY: "식비", CHARM: "쇼핑 · 뷰티", IQ: "문화 · 여가", ENDURANCE: "생활 · 고정비" };
const REWARD_NAMES = { ENERGY: "에너지", CHARM: "매력", IQ: "지능", ENDURANCE: "지구력" };
const EXHAUSTED_LABEL = "주황 · 예산 소진";
const SIGNAL_RANK = { GREEN: 1, GRAY: 2, RED: 3 };
const NO_RECORD_LABEL = "기록 없음";

export function judgmentReason(expense, signal) {
  const saved = Number(expense.savedAmount || 0);
  const comparison = saved > 0 ? `같은 스탯의 평균 소비보다 ${saved.toLocaleString("ko-KR")}원 적게 썼어요.` : saved < 0 ? `같은 스탯의 평균 소비보다 ${Math.abs(saved).toLocaleString("ko-KR")}원 많이 썼어요.` : "같은 스탯의 평균 소비와 비슷한 금액이에요.";
  if (expense.reasonCategory) return `${comparison} 입력한 지출 사유를 반영했어요.`;
  if (expense.zScore == null) return "이전 기록이 충분하지 않아 기본 기준으로 표시했어요.";
  if (expense.isRecurring && signal === "GRAY") return `${comparison} 반복 결제는 주의 판정에서 제외해 보통으로 표시했어요.`;
  return comparison;
}

// 스탯별 판정은 서버가 저장한 결과만 쓴다. 상세가 저장되기 전(V49 이전) 판정은 null —
// 보상 숫자나 지출 신호로 색을 추측하면 잘 아낀 날도 빨강으로 보이므로 「기록 없음」으로 둔다.
function signalForGroup(statType, judgment) {
  return judgment?.groupJudgments?.[statType]?.signal ?? null;
}

function rewardForGroup(judgment, statType) {
  const savedReward = judgment?.statRewards?.[statType];
  // 이전 버전의 판정에는 그룹별 보상 기록이 없어 statRewardPerType 하나만 남아 있다.
  // 그것을 모든 스탯에 복제하면 "전부 +6"처럼 잘못 보이므로 새 그룹별 기록만 표시한다.
  return savedReward == null ? 0 : Number(savedReward);
}

// "합리적으로", "과소비로" — 받침 있으면 "으로", 없거나 ㄹ 받침이면 "로"
function withRo(word) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return `${word}(으)로`;
  const final = code % 28;
  return final === 0 || final === 8 ? `${word}로` : `${word}으로`;
}

// "과소비였지만", "보통이었지만" — 받침이 있으면 "이었지만"
function withYeotJiman(word) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return `${word}였지만`;
  return code % 28 === 0 ? `${word}였지만` : `${word}이었지만`;
}

function groupReason(group, softenedFrom) {
  const detail = group.detail;
  if (!detail) return `이 판정은 스탯별 상세가 저장되기 전에 기록돼 예산 비교를 다시 보여 드릴 수 없어요. 이 스탯에서 ${group.total.toLocaleString("ko-KR")}원을 사용했어요.`;
  const monthly = Number(detail.monthlyBudget || 0).toLocaleString("ko-KR");
  const dailyBase = Number(detail.dailyBase || 0).toLocaleString("ko-KR");
  const available = Number(detail.availableToday || 0).toLocaleString("ko-KR");
  const today = Number(detail.todaySpent || 0).toLocaleString("ko-KR");
  const monthSpent = Number(detail.monthSpent || 0).toLocaleString("ko-KR");
  if (detail.budgetExhausted) return `월 예산 ${monthly}원을 이미 모두 사용해 오늘 사용할 수 있는 금액이 0원이에요. 오늘 지출은 없지만 더 절약할 예산도 남지 않아 예산 소진으로 표시했어요.`;
  const prefix = `월 예산 ${monthly}원, 하루 기본 예산 ${dailyBase}원이에요. 전날 남긴 금액의 절반만 반영해 오늘 사용할 수 있는 금액은 ${available}원이고, 오늘은 ${today}원 사용했어요. 이번 달 누적 지출은 ${monthSpent}원이에요.`;
  // 예외 지출 사유가 인정돼 완화된 그룹 — 금액 기준 설명(85% 이하 등)을 붙이면 실제 사용액과 어긋난다
  if (softenedFrom) return `${prefix} 1차 판정은 ${withYeotJiman(LABELS[softenedFrom])} 예외 지출 사유가 인정돼 ${withRo(LABELS[group.signal])} 바꿨어요.`;
  if (group.signal === "GREEN") return `${prefix} 오늘 사용액이 사용 가능 금액의 85% 이하라 여유가 있어 합리적으로 판정했어요.`;
  if (group.signal === "GRAY") return `${prefix} 오늘 사용액이 사용 가능 금액의 85%를 넘었지만 한도 안이라 보통으로 판정했어요.`;
  return `${prefix} 오늘 사용액이 사용 가능 금액을 초과해 주의로 판정했어요.`;
}

export default function JudgmentResults({ expenses, judgment }) {
  const signal = judgment?.signal || "GREEN";
  const coin = judgment?.coinReward ?? 0;
  const groupedExpenses = expenses.reduce((all, expense) => {
    const statType = expense.statType || "ENDURANCE";
    const group = all[statType] || { statType, items: [], total: 0 };
    group.items.push(expense);
    group.total += Number(expense.amount || 0);
    all[statType] = group;
    return all;
  }, {});
  const groups = Object.keys(STAT_LABELS).map((statType) => {
    const group = groupedExpenses[statType] || { statType, items: [], total: 0 };
    return { ...group, detail: judgment?.groupJudgments?.[statType], signal: signalForGroup(statType, judgment) };
  });
  const hasExhaustedGroup = groups.some((group) => group.detail?.budgetExhausted);

  return <div className="ledger-judgment-list">
    <section className="ledger-judgment-card ledger-reward-card" aria-label="일일 판정 보상">
      <div className="ledger-reward-heading"><div><span>오늘의 판정 보상</span><h2>{hasExhaustedGroup ? EXHAUSTED_LABEL : LABELS[signal]}</h2></div><span className={`ledger-signal-result ${hasExhaustedGroup ? "ledger-signal-result--exhausted" : `ledger-signal-result--${signal.toLowerCase()}`}`}>{hasExhaustedGroup ? EXHAUSTED_LABEL : LABELS[signal]}</span></div>
      <div className="ledger-reward-grid">
        <div className="ledger-reward-item ledger-reward-item--coin"><span className="ledger-reward-icon" aria-hidden="true"><CoinIcon /></span><span>판정 지급 코인</span><strong>+{coin.toLocaleString("ko-KR")}</strong></div>
        {groups.filter((group) => rewardForGroup(judgment, group.statType) > 0).map((group) => <div className="ledger-reward-item" key={group.statType}><span className="ledger-reward-icon" aria-hidden="true">✦</span><span>{REWARD_NAMES[group.statType]}</span><strong>+{rewardForGroup(judgment, group.statType)}</strong></div>)}
      </div>
      <p className="ledger-reward-note">판정으로 지급된 코인: +{coin.toLocaleString("ko-KR")} · 오늘 그룹 예산에서 절약한 금액: {Number(judgment?.savedAmount ?? 0).toLocaleString("ko-KR")}원</p>
      <p className="ledger-reward-note">같은 스탯 그룹의 지출을 합산해 하루 한 번 판정해요.</p>
    </section>
    {groups.map((group) => <GroupJudgmentCard key={group.statType} group={group} judgment={judgment} />)}
  </div>;
}

function GroupJudgmentCard({ group, judgment }) {
  const [open, setOpen] = useState(true);
  const reward = rewardForGroup(judgment, group.statType);
  const id = `judgment-group-${group.statType}`;
  const title = STAT_LABELS[group.statType] || "기타 지출";
  const exhausted = Boolean(group.detail?.budgetExhausted);
  const label = exhausted ? EXHAUSTED_LABEL : group.signal ? LABELS[group.signal] : NO_RECORD_LABEL;
  // 확정 결과가 1차 판정보다 순해졌으면 예외 지출 사유가 인정된 것이다 — 무엇이 바뀌었는지 함께 보여 준다.
  // 더 나빠진 경우(1차 뒤에 지출을 더 적거나 고침)는 사유 인정이 아니므로 이 문구를 쓰지 않는다.
  const initialSignal = judgment?.initialGroupJudgments?.[group.statType]?.signal;
  const changed = Boolean(initialSignal && group.signal && SIGNAL_RANK[group.signal] < SIGNAL_RANK[initialSignal]);
  const signalClass = exhausted ? "ledger-signal-result--exhausted" : `ledger-signal-result--${group.signal ? group.signal.toLowerCase() : "none"}`;
  const summary = group.detail
    ? `${group.items.length}건 합계 ${group.total.toLocaleString("ko-KR")}원 · 절약 ${Number(group.detail.savedAmount ?? 0).toLocaleString("ko-KR")}원 · 내일 +${Number(group.detail.nextDayCarry ?? 0).toLocaleString("ko-KR")}원`
    : `${group.items.length}건 합계 ${group.total.toLocaleString("ko-KR")}원`;
  return <article className="ledger-judgment-card ledger-judgment-card--toggle">
    <button type="button" className="ledger-judgment-toggle" aria-expanded={open} aria-controls={id} onClick={() => setOpen((value) => !value)}><span className="ledger-judgment-toggle-text"><strong>{title} 판정</strong><span>{summary}</span></span><span className={`ledger-signal-result ${signalClass}`}>{label}</span><span className="ledger-judgment-chevron" aria-hidden="true" /></button>
    {open && <div id={id} className="ledger-judgment-reason"><span>판정 이유</span><p>{groupReason(group, changed ? initialSignal : null)} {reward > 0 ? `${REWARD_NAMES[group.statType]} +${reward} 보상을 받았어요.` : ""}</p>{group.items.length > 0 && <div className="ledger-judgment-expenses">{group.items.map((expense) => <div className="ledger-judgment-expense-row" key={expense.id}><span><strong>{expense.item || "지출"}</strong><small>{Number(expense.amount || 0).toLocaleString("ko-KR")}원</small></span></div>)}</div>}</div>}
  </article>;
}
