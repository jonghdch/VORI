import "./WalletEntry.css";

// 3-step indicator (가계부 작성 / 소비 분석 / 확인).
// current: 1 | 2 | 3 — 현재 단계 (호출자가 사용자 화면 기준 번호로 전달)
// includeAnalysis: false 면 "소비 분석" 단계를 숨기고 2-step (1→2) 으로 표시.
function StepIndicator({ current, includeAnalysis = true }) {
  const all = [
    { label: "가계부 작성", key: "entry" },
    { label: "소비 분석", key: "analysis" },
    { label: "확인", key: "confirm" },
  ];
  const steps = (includeAnalysis ? all : all.filter((s) => s.key !== "analysis"))
    .map((s, i) => ({ ...s, num: i + 1 }));

  return (
    <div className="ledger-steps">
      {steps.map((s, i) => {
        const state =
          s.num < current ? "done" : s.num === current ? "active" : "pending";
        return (
          <div key={s.key} className="ledger-step-wrap">
            <div className={`ledger-step ledger-step-${state}`}>
              <div className="ledger-step-circle">{s.num}</div>
              <div className="ledger-step-label">{s.label}</div>
            </div>
            {i < steps.length - 1 && (
              <div
                className={`ledger-step-line ${
                  s.num < current ? "ledger-step-line-done" : ""
                }`}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

export default StepIndicator;
