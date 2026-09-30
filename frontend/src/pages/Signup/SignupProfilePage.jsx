import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { saveSpendingProfile } from "../../api/onboarding";
import "./SignupPage.css";

const STEPS = [
  {
    key: "monthlyIncome",
    type: "number",
    title: "한 달 수입은 얼마 정도인가요?",
    caption: "필수예요. 쇼핑·문화·생활비 지출을 처음 판정할 때 '내 수입 대비 큰 지출인지'의 기준이 돼요. 나중에 프로필 설정에서 바꿀 수 있어요.",
    placeholder: "예: 800000",
    unit: "원",
  },
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
  const isNumberStep = step.type === "number";
  // 숫자 단계는 0 이상 정수만 통과. 빈 값·음수·소수는 막는다.
  const numberValid = isNumberStep && /^\d+$/.test(String(selected));
  const canProceed = isNumberStep ? numberValid : Boolean(selected);
  const isLast = stepIndex === STEPS.length - 1;
  const progressPct = useMemo(
    () => Math.round(((stepIndex + 1) / STEPS.length) * 100),
    [stepIndex],
  );

  const select = (value) => {
    setProfile((current) => ({ ...current, [step.key]: value }));
  };

  // 서버에 보낼 형태 — 월 수입은 숫자, 나머지는 enum 문자열
  const toRequest = (p) => ({ ...p, monthlyIncome: Number(p.monthlyIncome) });

  const goNext = async () => {
    if (!canProceed || loading) return;
    if (!isLast) {
      setStepIndex((idx) => idx + 1);
      return;
    }

    setError("");
    setLoading(true);
    try {
      await saveSpendingProfile(toRequest(profile));
      window.dispatchEvent(new Event("vori:onboarding-done"));
      navigate("/onboarding");
    } catch (err) {
      setError(err.message || "소비 기준 저장 중 오류가 발생했어요");
    } finally {
      setLoading(false);
    }
  };

  // "나중에 하기" — 선택형 문항만 UNKNOWN 으로 채운다. 월 수입은 필수라 건너뛸 수 없다.
  const skip = async () => {
    if (!/^\d+$/.test(String(profile.monthlyIncome))) {
      setStepIndex(0);
      setError("월 수입은 꼭 입력해 주세요. 나머지는 나중에 해도 돼요.");
      return;
    }
    const skipped = STEPS.reduce(
      (acc, item) => ({ ...acc, [item.key]: item.type === "number" ? profile[item.key] : profile[item.key] || "UNKNOWN" }),
      {},
    );
    setError("");
    setLoading(true);
    try {
      await saveSpendingProfile(toRequest(skipped));
      window.dispatchEvent(new Event("vori:onboarding-done"));
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

          {isNumberStep ? (
            <label className="signup-field signup-income-field">
              <span className="signup-label">월 수입</span>
              <div className="signup-income-input">
                <input
                  type="number"
                  inputMode="numeric"
                  min="0"
                  step="10000"
                  className="signup-input"
                  placeholder={step.placeholder}
                  value={selected}
                  onChange={(e) => select(e.target.value.replace(/[^\d]/g, ""))}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      goNext();
                    }
                  }}
                  autoFocus
                />
                <span className="signup-income-unit">{step.unit}</span>
              </div>
              {numberValid && Number(selected) > 0 && (
                <span className="signup-hint">{Number(selected).toLocaleString("ko-KR")}원</span>
              )}
            </label>
          ) : (
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
          )}

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
              disabled={!canProceed || loading}
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
