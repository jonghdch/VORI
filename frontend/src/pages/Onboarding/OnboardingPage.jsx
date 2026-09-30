import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { completeOnboarding, getOnboardingStatus } from "../../api/onboarding";
import "../Signup/SignupPage.css";

const MEAL_LABEL = {
  UNDER_7: "7,000원 이하",
  MEAL_7_10: "7,000~10,000원",
  MEAL_10_15: "10,000~15,000원",
  OVER_15: "15,000원 이상",
  UNKNOWN: "아직 모름",
};

const AREA_LABEL = {
  FOOD_CAFE: "식비·카페",
  SHOPPING_BEAUTY: "쇼핑·뷰티",
  CULTURE_LEISURE: "문화·여가",
  TRANSPORT_LIVING: "교통·생활비",
  UNKNOWN: "아직 모름",
};

const GOAL_LABEL = {
  BUILD_RECORDING_HABIT: "지출 기록 습관 만들기",
  REDUCE_FOOD: "식비 줄이기",
  REDUCE_IMPULSE: "충동구매 줄이기",
  GROW_PET: "보리 키우기",
  UNKNOWN: "천천히 정하기",
};

function OnboardingPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getOnboardingStatus()
      .then((data) => {
        setStatus(data);
        if (data && !data.profileCompleted) navigate("/signup/profile", { replace: true });
      })
      .catch(() => navigate("/login", { replace: true }))
      .finally(() => setLoading(false));
  }, [navigate]);

  const profile = status?.profile;
  const ctaText = useMemo(() => {
    if (profile?.monthlyGoal === "REDUCE_FOOD") return "오늘 식비 기록하기";
    if (profile?.monthlyGoal === "GROW_PET") return "첫 기록으로 보리 키우기";
    return "첫 지출 기록하기";
  }, [profile?.monthlyGoal]);

  const finish = async (path) => {
    if (saving) return;
    setSaving(true);
    try {
      await completeOnboarding();
    } finally {
      window.dispatchEvent(new Event("vori:onboarding-done"));
      navigate(path);
    }
  };

  if (loading || !status) return null;

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
        <section className="signup-card signup-onboarding-card">
          <div className="signup-card-head">
            <p className="signup-step-text">시작 준비 완료</p>
            <h1 className="signup-title">내 소비 기준이 준비됐어요.</h1>
            <p className="signup-subtitle">
              첫 기록을 남기면 보리가 오늘 소비를 내 평소 기준으로 살펴봐요.
            </p>
          </div>

          <div className="signup-summary-grid">
            <div>
              <span>월 수입</span>
              <strong>
                {status?.monthlyIncome != null
                  ? `${Number(status.monthlyIncome).toLocaleString("ko-KR")}원`
                  : "아직 모름"}
              </strong>
            </div>
            <div>
              <span>평소 한 끼</span>
              <strong>{MEAL_LABEL[profile?.mealCostBand] || "아직 모름"}</strong>
            </div>
            <div>
              <span>자주 쓰는 영역</span>
              <strong>{AREA_LABEL[profile?.primarySpendArea] || "아직 모름"}</strong>
            </div>
            <div>
              <span>이번 달 목표</span>
              <strong>{GOAL_LABEL[profile?.monthlyGoal] || "천천히 정하기"}</strong>
            </div>
          </div>

          <div className="signup-actions">
            <button
              type="button"
              className="signup-secondary"
              onClick={() => navigate("/signup/profile")}
              disabled={saving}
            >
              수정하기
            </button>
            <button
              type="button"
              className="signup-submit signup-action-primary"
              onClick={() => finish("/wallet/new")}
              disabled={saving}
            >
              {ctaText}
            </button>
          </div>

          <button
            type="button"
            className="signup-skip"
            onClick={() => finish("/home")}
            disabled={saving}
          >
            홈으로 가기
          </button>
        </section>
      </main>

      <footer className="signup-footer">졸업작품 © 2026 VORI Team</footer>
    </div>
  );
}

export default OnboardingPage;
