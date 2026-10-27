import { useEffect, useState } from "react";
import CoinIcon from "../../components/CoinIcon";
import Icon from "../../components/Icon";
import SignalChip from "../../components/SignalChip";
import { SIGNAL_STATUS } from "../../signalLabels";
import { REASON_CHIPS } from "./reasonChips";

const STAT_TYPES = ["ENERGY", "CHARM", "IQ", "ENDURANCE"];
const STAT_LABELS = {
  ENERGY: "식비",
  CHARM: "쇼핑·뷰티",
  IQ: "문화·여가",
  ENDURANCE: "생활·고정비",
};
const REWARD_NAMES = { ENERGY: "에너지", CHARM: "매력", IQ: "지능", ENDURANCE: "지구력" };
// WalletPage 의 STAT_REWARDS 와 같은 색을 쓴다.
const STAT_REWARD_COLORS = {
  ENERGY: "#7fb069",
  CHARM: "#e07a7a",
  IQ: "#6b9bd1",
  ENDURANCE: "#c9913f",
};
const NO_RECORD_LABEL = "기록 없음";

// 판정 규칙(DailyJudgmentService)이 바뀌면 여기만 고친다.
export const RULE_TEXT = {
  thresholds: "85% 이하 여유 · 100% 이하 적정 · 넘으면 초과",
  carryFormula: "오늘 쓸 수 있는 돈 = 하루 기본 예산 + 전날 남긴 돈의 절반.",
  overrun: "넘은 만큼 내일 예산이 줄어요.",
  questionGuidance: () => "사유가 인정되면 이 그룹 판정을 가볍게 봐요. 그래도 넘은 만큼 내일 예산이 줄어드는 건 같아요.",
  // 「여유로」·「적정으로」 — 조사는 withRo 가 받침을 보고 고른다
  summarySoftened: (groups, status) => `${groups}는 사유가 인정돼 ${withRo(status)} 조정됐어요.`,
  summarySoftenedMixed: (groups) => `${groups}는 사유가 인정돼 판정이 조정됐어요.`,
  // 사유로 풀린 그룹과 그대로 넘은 그룹이 같이 있으면 한 문장으로 — 맨 위 「초과」 칩이 왜 초과인지 보이게
  summarySoftenedAndExceeded: (softGroups, status, overGroups) =>
    `${softGroups}는 사유가 인정돼 ${status ? `${withRo(status)} ` : "판정이 "}조정됐고, ${overGroups}는 오늘 쓸 수 있는 돈을 넘었어요.`,
  summaryExceeded: (groups) => `${groups}는 오늘 쓸 수 있는 돈을 넘었어요. ${RULE_TEXT.overrun}`,
  summaryExhausted: (groups) => `${groups}는 이번 달 예산을 다 써서 예산 소진으로 표시했어요.`,
  summaryAllGreen: "모든 그룹이 쓸 수 있는 돈의 85% 안이에요.",
  summaryNoExceeded: "넘은 그룹은 없어요.",
  // 스탯별 상세가 저장되기 전(V49 이전) 판정 — 그룹을 따질 수 없으니 맨 위 칩과 어긋나는 말을 하지 않는다
  summaryNoDetail: "그룹별 상세가 저장되기 전 판정이라 그룹별 비교를 보여 드릴 수 없어요.",
  groupExhausted: (monthlyBudget) => `이번 달 예산 ${monthlyBudget}원을 다 써서 오늘 쓸 수 있는 돈이 0원이에요.`,
  groupGreen: "쓸 수 있는 돈의 85% 안에서 썼어요.",
  groupGray: "쓸 수 있는 돈의 85%를 넘었지만 한도 안이에요.",
  groupOverrun: (amount) => `쓸 수 있는 돈보다 ${amount}원 더 썼어요. ${RULE_TEXT.overrun}`,
  firstJudgmentOverrun: (availableToday) => `오늘 쓸 수 있는 ${availableToday}원을 넘었어요.`,
  softenedJudgment: (status, overrun) => `사유가 인정돼 ${status} 조정했어요.${overrun ? ` ${RULE_TEXT.overrun}` : ""}`,
};

const formatWon = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;

export function judgmentReason(expense, signal) {
  const saved = Number(expense.savedAmount || 0);
  const comparison = saved > 0
    ? `같은 스탯의 평균 소비보다 ${saved.toLocaleString("ko-KR")}원 적게 썼어요.`
    : saved < 0
      ? `같은 스탯의 평균 소비보다 ${Math.abs(saved).toLocaleString("ko-KR")}원 많이 썼어요.`
      : "같은 스탯의 평균 소비와 비슷한 금액이에요.";
  if (expense.reasonCategory) return `${comparison} 입력한 지출 사유를 반영했어요.`;
  if (expense.zScore == null) return "이전 기록이 충분하지 않아 기본 기준으로 표시했어요.";
  if (expense.isRecurring && signal === "GRAY") return `${comparison} 반복 결제는 초과 판정에서 제외해 적정으로 표시했어요.`;
  return comparison;
}

// 그룹 신호는 서버가 저장한 값만 표시한다. 없는 신호를 지출 신호로 추측하지 않는다.
function signalForGroup(statType, judgment) {
  return judgment?.groupJudgments?.[statType]?.signal ?? null;
}

function rewardForGroup(judgment, statType) {
  const savedReward = judgment?.statRewards?.[statType];
  // 이전 판정의 그룹별 보상은 알 수 없으므로 그룹마다 복제해 보여 주지 않는다.
  return savedReward == null ? 0 : Number(savedReward);
}

// "여유로", "적정으로" — 받침 있으면 "으로", 없거나 ㄹ 받침이면 "로"
function withRo(word) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  if (code < 0 || code > 11171) return `${word}(으)로`;
  const final = code % 28;
  return final === 0 || final === 8 ? `${word}로` : `${word}으로`;
}

// 그 그룹에서 사유가 인정된 지출. 승인된 질문 id 는 받지 않으므로 지출 신호(처음 RED → 최종 RED 아님)로 어림한다.
export function recognizedExpensesFor(statType, expenses) {
  return expenses.filter((expense) =>
    (expense.statType || "ENDURANCE") === statType
      && expense.signalInitial === "RED"
      && expense.signalFinal
      && expense.signalFinal !== "RED"
  );
}

// 예외 지출 사유가 인정돼 풀린 그룹인지. 1차보다 순해졌다는 것만으로는 부족하다 — 판정 대기(PENDING) 중에
// 지출을 고치거나 지워도 확정 때 다시 계산돼 순해진다. 서버는 1차가 RED 인 그룹을, 인정된 지출이 있을 때만
// 그 지출의 신호까지 완화하므로(DailyJudgmentService) 같은 조건을 본다.
export function isSoftenedByReason(statType, judgment, expenses) {
  const initial = judgment?.initialGroupJudgments?.[statType]?.signal;
  const final = judgment?.groupJudgments?.[statType]?.signal;
  if (initial !== "RED" || !final || final === "RED") return false;
  return recognizedExpensesFor(statType, expenses).length > 0;
}

function reasonCategoryLabel(category) {
  return REASON_CHIPS.find((chip) => chip.value === category)?.label;
}

function groupReason(group) {
  const detail = group.detail;
  if (!detail) return `이 판정은 스탯별 상세가 저장되기 전에 기록돼 예산 비교를 다시 보여 드릴 수 없어요. 이 그룹에서 ${formatWon(group.total)}을 사용했어요.`;
  const available = Number(detail.availableToday || 0);
  const todaySpent = Number(detail.todaySpent || 0);
  if (detail.budgetExhausted) return RULE_TEXT.groupExhausted(Number(detail.monthlyBudget || 0).toLocaleString("ko-KR"));
  if (group.signal === "GREEN") return RULE_TEXT.groupGreen;
  if (group.signal === "GRAY") return RULE_TEXT.groupGray;
  if (group.signal === "RED") return RULE_TEXT.groupOverrun(Math.max(0, todaySpent - available).toLocaleString("ko-KR"));
  return "그룹 판정 기록을 확인할 수 없어요.";
}

function JudgmentTimeline({ group, initialSignal, recognizedExpenses }) {
  const detail = group.detail;
  const finalSignal = group.signal;
  const available = Number(detail.availableToday || 0).toLocaleString("ko-KR");
  const spent = Number(detail.todaySpent || 0);
  const allowed = Number(detail.availableToday || 0);
  return (
    <ol className="ja-timeline" aria-label="판정 과정">
      <li className={`ja-timeline-step ja-timeline-step--${initialSignal.toLowerCase()}`}>
        <div className="ja-timeline-copy">
          <strong>1차 판정 · {SIGNAL_STATUS[initialSignal]}</strong>
          <span>{RULE_TEXT.firstJudgmentOverrun(available)}</span>
        </div>
      </li>
      <li className="ja-timeline-step ja-timeline-step--neutral">
        <div className="ja-timeline-copy">
          <strong>사유 인정</strong>
          {recognizedExpenses.map((expense) => {
            const reason = reasonCategoryLabel(expense.reasonCategory);
            return (
              <span key={expense.id}>
                {expense.item || "지출"}{reason ? ` · ${reason}` : ""}
              </span>
            );
          })}
        </div>
      </li>
      <li className={`ja-timeline-step ja-timeline-step--${finalSignal.toLowerCase()}`}>
        <div className="ja-timeline-copy">
          <strong>최종 판정 · {SIGNAL_STATUS[finalSignal]}</strong>
          <span>{RULE_TEXT.softenedJudgment(withRo(SIGNAL_STATUS[finalSignal]), spent > allowed)}</span>
        </div>
      </li>
    </ol>
  );
}

export default function JudgmentResults({ expenses, judgment }) {
  const coin = Number(judgment?.coinReward ?? 0);
  const paid = Boolean(judgment?.rewarded);
  const groupedExpenses = expenses.reduce((all, expense) => {
    const statType = expense.statType || "ENDURANCE";
    const group = all[statType] || { statType, items: [], total: 0 };
    group.items.push(expense);
    group.total += Number(expense.amount || 0);
    all[statType] = group;
    return all;
  }, {});
  const groups = STAT_TYPES.map((statType) => {
    const group = groupedExpenses[statType] || { statType, items: [], total: 0 };
    return {
      ...group,
      detail: judgment?.groupJudgments?.[statType],
      signal: signalForGroup(statType, judgment),
    };
  });

  return (
    <div className="ja-results">
      <section className="hm-card ja-reward-card" aria-label="일일 판정 보상">
        <div className="ja-reward-head">
          <h2>{paid ? "받은 보상" : "자정에 받을 보상"}</h2>
          <span className="ja-reward-status">{paid ? "지급 완료" : "자정 지급"}</span>
        </div>
        <div className="ja-coin-reward">
          <CoinIcon size={28} />
          <strong>+{coin.toLocaleString("ko-KR")}</strong>
          <span>코인</span>
        </div>
        <div className="ja-stat-rewards">
          {groups.filter((group) => rewardForGroup(judgment, group.statType) > 0).map((group) => {
            const reward = rewardForGroup(judgment, group.statType);
            return (
              <span className="ja-stat-reward" key={group.statType}>
                <i className="ja-stat-dot" style={{ "--ja-stat-color": STAT_REWARD_COLORS[group.statType] }} aria-hidden="true" />
                {REWARD_NAMES[group.statType]} +{reward}
              </span>
            );
          })}
        </div>
        <p className="ja-reward-saved">오늘 남긴 돈 {formatWon(judgment?.savedAmount)}</p>
        {!paid && <p className="ja-reward-note">보상은 오늘 자정에 한 번 들어와요. 판정 뒤에 지출을 고쳐도 보상은 바뀌지 않아요.</p>}
      </section>

      <h2 className="ja-section-title">그룹별 판정</h2>
      <div className="ja-groups">
        {groups.map((group) => (
          <GroupJudgmentCard key={group.statType} group={group} judgment={judgment} />
        ))}
      </div>

      <details className="ja-criteria">
        <summary>
          <span className="ja-criteria-title"><Icon name="info" size={17} /> 판정 기준 자세히 보기</span>
          <span className="ja-criteria-thresholds">{RULE_TEXT.thresholds}</span>
        </summary>
        <div className="ja-criteria-copy">
          <p>그룹마다 오늘 쓸 수 있는 돈과 비교해요.</p>
          <p>{RULE_TEXT.carryFormula} {RULE_TEXT.overrun}</p>
          <p>그날 판정은 네 그룹 중 가장 무거운 판정을 따라요.</p>
        </div>
      </details>
    </div>
  );
}

function GroupJudgmentCard({ group, judgment }) {
  const initialSignal = judgment?.initialGroupJudgments?.[group.statType]?.signal;
  // 사유로 풀린 그룹에만 판정 과정(1차 → 사유 인정 → 최종)을 보여 준다. 지출을 고쳐 순해진 그룹은 평소 이유 한 줄.
  const changed = isSoftenedByReason(group.statType, judgment, group.items);
  const exhausted = Boolean(group.detail?.budgetExhausted);
  const recognizedExpenses = changed ? recognizedExpensesFor(group.statType, group.items) : [];
  const [open, setOpen] = useState(() => changed || group.signal === "RED" || exhausted);
  // 확정 직후에는 답변 전 지출 목록으로 먼저 그려져 「사유 인정」이 아직 안 보인다 — 새 목록이 와서
  // 사유로 풀린 그룹이 되면 그때 펼친다(처음 펼침 상태만 정하면 접힌 채 남는다).
  useEffect(() => {
    if (changed) setOpen(true);
  }, [changed]);
  const reward = rewardForGroup(judgment, group.statType);
  const id = `judgment-group-${group.statType}`;
  const available = group.detail ? Number(group.detail.availableToday || 0) : 0;
  const todaySpent = group.detail ? Number(group.detail.todaySpent || 0) : 0;
  const monthlyBudget = group.detail ? Number(group.detail.monthlyBudget || 0) : 0;
  const dailyBase = group.detail ? Number(group.detail.dailyBase || 0) : 0;
  const monthSpent = group.detail ? Number(group.detail.monthSpent || 0) : 0;
  const carry = Number(group.detail?.nextDayCarry || 0);
  const summary = group.detail
    ? (
      <>
        <span className="ja-group-amounts">
          <strong>{formatWon(todaySpent)}</strong>
          <span> / {formatWon(available)}</span>
        </span>
        {carry > 0 && <small>내일 +{formatWon(carry)}</small>}
      </>
    )
    : <span className="ja-group-total">{formatWon(group.total)}</span>;

  return (
    <article className="hm-card ja-group-card">
      <button
        type="button"
        className="ja-group-toggle"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((value) => !value)}
      >
        <span className="ja-group-main">
          <strong>{STAT_LABELS[group.statType]}</strong>
          {exhausted
            ? <SignalChip signal="GRAY" label="예산 소진" />
            : group.signal
              ? <SignalChip signal={group.signal} />
              : <span className="ja-chip-none">{NO_RECORD_LABEL}</span>}
          {/* 접힌 줄만 보면 예산을 크게 넘은 그룹에 「여유」가 붙어 헷갈린다 — 사유로 풀린 것임을 같이 보인다 */}
          {changed && <span className="ja-group-softened">사유 인정</span>}
        </span>
        <span className="ja-group-metrics">
          {summary}
        </span>
        <Icon name="chevronDown" className={`ja-group-chevron${open ? " ja-group-chevron--open" : ""}`} size={19} />
      </button>
      {open && (
        <div className="ja-group-detail" id={id}>
          {group.detail ? (
            <div className="ja-budget-pair">
              <div><span>쓸 수 있던 돈</span><strong>{formatWon(available)}</strong></div>
              <div><span>쓴 돈</span><strong>{formatWon(todaySpent)}</strong></div>
            </div>
          ) : (
            <p className="ja-no-detail">스탯별 상세가 저장되기 전 판정이라 예산 비교를 확인할 수 없어요.</p>
          )}

          {group.detail && changed && initialSignal && group.signal && (
            <JudgmentTimeline
              group={group}
              initialSignal={initialSignal}
              recognizedExpenses={recognizedExpenses}
            />
          )}

          {group.detail && !changed && (
            <>
              <p className="ja-group-reason">{groupReason(group)}</p>
              <p className="ja-group-monthly">
                월 예산 {formatWon(monthlyBudget)} · 하루 기본 {formatWon(dailyBase)} · 이번 달 쓴 돈 {formatWon(monthSpent)}
              </p>
            </>
          )}

          {group.items.length > 0 && (
            <ul className="ja-expense-list" aria-label={`${STAT_LABELS[group.statType]} 지출`}>
              {group.items.map((expense) => (
                <li key={expense.id}>
                  <span>{expense.item || "지출"}</span>
                  <strong>{formatWon(expense.amount)}</strong>
                </li>
              ))}
            </ul>
          )}

          {reward > 0 && (
            <p className="ja-group-reward">
              {REWARD_NAMES[group.statType]} +{reward} · {judgment?.rewarded ? "받았어요" : "자정에 받아요"}
            </p>
          )}
        </div>
      )}
    </article>
  );
}
