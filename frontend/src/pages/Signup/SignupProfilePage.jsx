import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { saveSpendingProfile } from "../../api/onboarding";
import "./SignupPage.css";

const STEPS = [
  {
    key: "monthlyBudgetBand",
    title: "한 달에 자유롭게 쓸 수 있는 돈은 어느 정도인가요?",
    caption: "처음 판정할 때 소비 규모를 너무 크게 오해하지 않게 도와줘요.",
    options: [
      ["UNDER_20", "20만원 이하"],
      ["BAND_20_40", "20만~40만원"],
      ["BAND_40_70", "40만~70만원"],
      ["OVER_70", "70만원 이상"],
      ["UNKNOWN", "잘 모르겠어요"],
    ],
  },
  {
    key: "mealCostBand",
    title: "평소 한 끼에 얼마 정도 쓰나요?",
    caption: "식비 판정의 초기 기준으로만 부드럽게 반영돼요.",
    options: [
      ["UNDER_7", "7,000원 이하"],
      ["MEAL_7_10", "7,000~10,000원"],
      ["MEAL_10_15", "10,000~15,000원"],
      ["OVER_15", "15,000원 이상"],
      ["UNKNOWN", "잘 모르겠어요"],
    ],
  },
  {
    key: "primarySpendArea",
    title: "요즘 가장 자주 돈을 쓰는 곳은 어디인가요?",
    caption: "첫 기록 화면에서 더 맞는 예시를 보여주는 데 사용해요.",
    options: [
      ["FOOD_CAFE", "식비·카페"],
      ["SHOPPING_BEAUTY", "쇼핑·뷰티"],
      ["CULTURE_LEISURE", "문화·여가"],
      ["TRANSPORT_LIVING", "교통·생활비"],
      ["UNKNOWN", "잘 모르겠어요"],
    ],
  },
  {
    key: "spendingHabit",
    title: "내 소비 습관에 가장 가까운 건 무엇인가요?",
    caption: "AI가 이유를 물을 때 더 자연스러운 문맥으로 이어갈 수 있어요.",
    options: [
      ["PLANNED", "계획한 것만 사는 편"],
      ["NEED_BASED", "필요하면 바로 사는 편"],
      ["EVENT_SENSITIVE", "할인이나 이벤트에 약한 편"],
      ["MOOD_BASED", "기분에 따라 쓰는 편"],
      ["UNKNOWN", "잘 모르겠어요"],
    ],
  },
  {
    key: "monthlyGoal",
    title: "이번 달 VORI에서 가장 해보고 싶은 건 무엇인가요?",
    caption: "가입 직후 보여줄 시작 행동을 고르는 데 사용해요.",
    options: [
      ["BUILD_RECORDING_HABIT", "지출 기록 습관 만들기"],
      ["REDUCE_FOOD", "식비 줄이기"],
      ["REDUCE_IMPULSE", "충동구매 줄이기"],
      ["GROW_PET", "보리 키우기"],
      ["UNKNOWN", "잘 모르겠어요"],
    ],
  },
];

const initialProfile = STEPS.reduce((acc, step) => ({ ...acc, [step.key]: "" }), {});

function SignupProfilePage() {
  const navigate = useNavigate();
  const [stepIndex, setStepIndex] = useState(0);
  const [profile, setProfile] = useState(initialProfile);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const step = STEPS[stepIndex];
  const selected = profile[step.key];
  const isLast = stepIndex === STEPS.length - 1;
  const progressPct = useMemo(
    () => Math.round(((stepIndex + 1) / STEPS.length) * 100),
    [stepIndex],
  );

  const select = (value) => {
    setProfile((current) => ({ ...current, [step.key]: value }));
  };

  const goNext = async () => {
    if (!selected || loading) return;
    if (!isLast) {
      setStepIndex((idx) => idx + 1);
      return;
    }

    setError("");
    setLoading(true);
    try {
      await saveSpendingProfile(profile);
      navigate("/onboarding");
    } catch (err) {
      setError(err.message || "소비 기준 저장 중 오류가 발생했어요");
    } finally {
      setLoading(false);
    }
  };

  const skip = async () => {
    const skipped = STEPS.reduce((acc, item) => ({ ...acc, [item.key]: profile[item.key] || "UNKNOWN" }), {});
    setError("");
    setLoading(true);
    try {
      await saveSpendingProfile(skipped);
      navigate("/onboarding");
    } catch (err) {
      setError(err.message || "소비 기준 저장 중 오류가 발생했어요");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="signup">
      <header className="signup-header">
        <button
          type="button"
          className="signup-logo-btn"
          onClick={() => navigate("/")}
          aria-label="VORI 홈으로"
        >
          VORI
        </button>
      </header>

      <main className="signup-main">
        <section className="signup-card signup-profile-card">
          <div className="signup-card-head">
            <p className="signup-step-text">{stepIndex + 1} / {STEPS.length}</p>
            <div className="signup-progress" aria-hidden="true">
              <span style={{ width: `${progressPct}%` }} />
            </div>
            <h1 className="signup-title">{step.title}</h1>
            <p className="signup-subtitle">{step.caption}</p>
          </div>

          <div className="signup-option-list" role="radiogroup" aria-label={step.title}>
            {step.options.map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={`signup-option${selected === value ? " is-selected" : ""}`}
                onClick={() => select(value)}
                aria-pressed={selected === value}
              >
                {label}
              </button>
            ))}
          </div>

          {error && (
            <p className="signup-hint signup-hint-error" role="alert">
              {error}
            </p>
          )}

          <div className="signup-actions">
            <button
              type="button"
              className="signup-secondary"
              onClick={() => (stepIndex === 0 ? navigate("/signup") : setStepIndex((idx) => idx - 1))}
              disabled={loading}
            >
              이전
            </button>
            <button
              type="button"
              className="signup-submit signup-action-primary"
              onClick={goNext}
              disabled={!selected || loading}
            >
              {loading ? "저장 중…" : isLast ? "기준 설정 완료" : "다음"}
            </button>
          </div>

          <button type="button" className="signup-skip" onClick={skip} disabled={loading}>
            나중에 하기
          </button>
        </section>
      </main>

      <footer className="signup-footer">졸업작품 © 2026 VORI Team</footer>
    </div>
  );
}

export default SignupProfilePage;
