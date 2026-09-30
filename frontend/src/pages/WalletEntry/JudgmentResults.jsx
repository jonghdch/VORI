const LABELS = { GREEN: "초록 · 합리적", GRAY: "노랑 · 보통", RED: "주황 · 주의" };
const REASONS = {
  CEREMONY: "경조사처럼 관계와 예의를 위해 필요한 지출로 인정되어 초록으로 조정됐어요.",
  EMERGENCY: "갑작스러운 상황에서 피하기 어려운 긴급 지출로 인정되어 초록으로 조정됐어요.",
  SELF_INVEST: "학습·성장 등 미래를 위한 자기투자 지출로 인정되어 초록으로 조정됐어요.",
  SOCIAL: "사회생활을 위한 지출이라는 사유를 반영해 노랑으로 조정했어요.",
  IMPULSE: "답변을 충동적인 소비로 분류해 금액 기준의 판정을 유지했어요.",
  ETC: "답변에서 판정을 조정할 인정 사유를 확인하지 못해 금액 기준의 판정을 유지했어요.",
};

export function judgmentReason(expense, signal) {
  const saved = Number(expense.savedAmount || 0);
  const comparison = saved > 0 ? `같은 스탯의 평균 소비보다 ${saved.toLocaleString("ko-KR")}원 적게 썼어요.` : saved < 0 ? `같은 스탯의 평균 소비보다 ${Math.abs(saved).toLocaleString("ko-KR")}원 많이 썼어요.` : "같은 스탯의 평균 소비와 비슷한 금액이에요.";
  if (expense.reasonCategory) return `${comparison} ${REASONS[expense.reasonCategory] || "입력한 사유를 반영했어요."}`;
  if (expense.zScore == null) return "이전 기록이 5건 미만이거나 소비 금액의 변동이 너무 작아 통계 비교가 어려워요. 충분한 기록이 쌓일 때까지 기본 초록으로 표시해요.";
  if (expense.isRecurring && signal === "GRAY") return `${comparison} 반복 결제는 주황 판정에서 제외돼 노랑으로 표시했어요.`;
  if (signal === "GREEN") return `${comparison} 평균과 평소 변동 폭을 함께 비교한 결과 절약 구간이에요.`;
  if (signal === "GRAY") return `${comparison} 평소 변동 폭 안에 있어 보통 구간으로 판정했어요.`;
  return `${comparison} 평소 변동 폭을 고려해도 주의 구간을 넘었어요. 필요한 지출이었다면 사유를 작성해 주세요.`;
}

export default function JudgmentResults({ expenses, judgment }) {
  const signal = judgment?.signal || "GREEN";
  const coin = judgment?.coinReward ?? 0;
  const stat = judgment?.statRewardPerType ?? 0;
  const statRewards = ["에너지", "매력", "지능", "지구력"];

  return <div className="ledger-judgment-list">
    <section className="ledger-judgment-card ledger-reward-card" aria-label="일일 판정 보상">
      <div className="ledger-reward-heading">
        <div>
          <span>오늘의 판정 보상</span>
          <h2>{LABELS[signal]}</h2>
        </div>
        <span className={`ledger-signal-result ledger-signal-result--${signal.toLowerCase()}`}>{LABELS[signal]}</span>
      </div>
      <div className="ledger-reward-grid">
        <div className="ledger-reward-item ledger-reward-item--coin">
          <span className="ledger-reward-icon" aria-hidden="true">🪙</span>
          <span>보유 코인</span>
          <strong>+{coin.toLocaleString("ko-KR")}</strong>
        </div>
        {statRewards.map((name) => (
          <div className="ledger-reward-item" key={name}>
            <span className="ledger-reward-icon" aria-hidden="true">✦</span>
            <span>{name}</span>
            <strong>+{stat}</strong>
          </div>
        ))}
      </div>
      <p className="ledger-reward-note">보상은 지출 금액과 무관하며 하루 판정이 처음 완료될 때 한 번만 지급돼요. 스탯은 현재 키우는 펫이 있을 때 반영됩니다.</p>
      {judgment?.alreadyJudged && <p role="status">이미 지급된 날짜라 보상을 다시 지급하지 않았어요.</p>}
    </section>
    {expenses.map((expense) => {
      const itemSignal = expense.signalFinal || expense.signalInitial || "GRAY";
      return <article key={expense.id} className="ledger-judgment-card"><div className="ledger-judgment-card-head"><div><strong>{expense.item || "지출"}</strong><span>{Number(expense.amount).toLocaleString("ko-KR")}원</span></div><span className={`ledger-signal-result ledger-signal-result--${itemSignal.toLowerCase()}`}>{LABELS[itemSignal]}</span></div><div className="ledger-judgment-reason"><span>판정 이유</span><p>{judgmentReason(expense, itemSignal)}</p></div></article>;
    })}
  </div>;
}
