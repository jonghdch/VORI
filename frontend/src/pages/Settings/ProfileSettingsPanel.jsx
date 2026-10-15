import { useEffect, useState } from "react";
import { getMe, updateMe } from "../../api/user";
import "./ProfileSettingsPanel.css";

const BUDGET_FOCUS_OPTIONS = [
  { value: "ENERGY", label: "식비 · 카페", description: "에너지 예산을 더 배정" },
  { value: "CHARM", label: "쇼핑 · 뷰티", description: "매력 예산을 더 배정" },
  { value: "IQ", label: "문화 · 여가", description: "지능 예산을 더 배정" },
  { value: "ENDURANCE", label: "생활 · 교통", description: "지구력 예산을 더 배정" },
];

const formFromUser = (user) => ({
  nickname: user?.nickname || "",
  name: user?.name || "",
  age: user?.age ?? "",
  job: user?.job || "",
  monthlyIncome: user?.monthlyIncome ?? "",
  monthlyBudget: user?.monthlyBudget ?? "",
  budgetFocusStats: Array.isArray(user?.budgetFocusStats) ? user.budgetFocusStats : [],
});

// 환경설정 > 프로필 탭. 내 정보 수정. (칭호는 도감 칭호 탭에서 바꾼다)
function ProfileSettingsPanel({ user, onUserUpdate }) {
  const [initial, setInitial] = useState(() => formFromUser(user));
  const [form, setForm] = useState(initial);
  const [email, setEmail] = useState(user?.email || "");
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  // /auth/me 응답에는 헤더용 최소 정보만 있으므로, 프로필 상세값은 별도 API에서 읽는다.
  useEffect(() => {
    getMe()
      .then((current) => {
        const next = formFromUser(current);
        setInitial(next);
        setForm(next);
        setEmail(current.email || "");
        onUserUpdate(current);
      })
      .catch((err) => setError(err.message || "프로필을 불러오지 못했어요."));
  }, [onUserUpdate]);

  // 오른쪽 바로가기에서 예산 입력 영역으로 바로 들어온 경우, 프로필을 불러온 뒤에도
  // 해당 영역이 확실히 보이도록 한 번 스크롤한다.
  useEffect(() => {
    if (window.location.hash !== "#monthly-budget") return;
    const frame = window.requestAnimationFrame(() => {
      document.getElementById("monthly-budget")?.scrollIntoView({ block: "start", behavior: "smooth" });
    });
    return () => window.cancelAnimationFrame(frame);
  }, []);

  const changed = JSON.stringify(form) !== JSON.stringify(initial);
  const setField = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    setNotice("");
    setError("");
  };
  const toggleBudgetFocus = (statType) => {
    setForm((current) => {
      const selected = new Set(current.budgetFocusStats);
      if (selected.has(statType)) selected.delete(statType);
      else selected.add(statType);
      return { ...current, budgetFocusStats: [...selected].sort() };
    });
    setNotice("");
    setError("");
  };

  const handleSave = async (event) => {
    event.preventDefault();
    // 가입과 같은 규칙 — 서버(ProfileUpdateRequest)도 같은 기준으로 거절한다.
    const nickname = form.nickname.trim();
    const name = form.name.trim();
    if (nickname.length < 2 || nickname.length > 12) {
      setError("닉네임은 2~12자로 입력해 주세요.");
      return;
    }
    if (name.length < 2) {
      setError("이름은 2~30자로 입력해 주세요.");
      return;
    }
    const income = form.monthlyIncome === "" ? null : Number(form.monthlyIncome);
    const budget = form.monthlyBudget === "" ? null : Number(form.monthlyBudget);
    if (budget != null && income == null) {
      setError("월 예산을 입력하려면 월 수입을 먼저 입력해 주세요.");
      return;
    }
    if (budget != null && income != null && budget > income) {
      setError("월 예산은 월 수입을 넘을 수 없어요.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const updated = await updateMe({
        nickname,
        name,
        age: form.age === "" ? null : Number(form.age),
        job: form.job.trim() || null,
        monthlyIncome: income,
        monthlyBudget: budget,
        budgetFocusStats: form.budgetFocusStats,
      });
      onUserUpdate(updated);
      const next = formFromUser(updated);
      setInitial(next);
      setForm(next);
      setEmail(updated.email || "");
      setNotice("프로필을 저장했어요.");
    } catch (err) {
      setError(err.message || "프로필 저장에 실패했어요.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="profile-settings">
      <form className="profile-form" onSubmit={handleSave}>
        <label>
          <span>이메일</span>
          <input value={email} disabled />
          <small>이메일은 로그인 계정으로 사용됩니다.</small>
        </label>
        <label>
          <span>닉네임 <b>필수</b></span>
          <input value={form.nickname} maxLength="12" onChange={setField("nickname")} placeholder="표시할 닉네임" />
        </label>
        <label>
          <span>이름 <b>필수</b></span>
          <input value={form.name} maxLength="30" onChange={setField("name")} placeholder="이름을 입력하세요" />
        </label>
        <div className="profile-form-grid">
          <label>
            <span>나이</span>
            <input type="number" min="1" max="120" value={form.age} onChange={setField("age")} placeholder="예: 23" />
          </label>
          <label>
            <span>직업</span>
            <input value={form.job} maxLength="50" onChange={setField("job")} placeholder="예: 대학생" />
          </label>
        </div>
        <label>
          <span>월 수입</span>
          <div className="profile-money-input">
            <input type="number" min="0" value={form.monthlyIncome} onChange={setField("monthlyIncome")} placeholder="예: 2500000" />
            <em>원</em>
          </div>
          <small>월 수입은 소비 패턴의 초기 기준을 잡는 데 사용돼요.</small>
        </label>
        <section id="monthly-budget" className="profile-budget-section" aria-label="월 예산 설정">
          <label>
            <span>월 예산</span>
            <div className="profile-money-input">
              <input type="number" min="0" max={form.monthlyIncome === "" ? undefined : form.monthlyIncome} value={form.monthlyBudget} onChange={setField("monthlyBudget")} placeholder="예: 800000" />
              <em>원</em>
            </div>
            <small>이번 달 가계부로 관리할 총금액이에요. 월 수입을 넘을 수 없어요.</small>
          </label>
          <fieldset className="profile-budget-focus">
          <legend>예산을 더 배정할 분야</legend>
          <p>해당하는 항목을 모두 선택하세요. 선택한 분야는 기본 배정 비중보다 더 많은 예산을 받아요.</p>
          <div className="profile-budget-focus-options">
            {BUDGET_FOCUS_OPTIONS.map((option) => {
              const checked = form.budgetFocusStats.includes(option.value);
              return (
                <label key={option.value} className={`profile-budget-focus-option${checked ? " is-checked" : ""}`}>
                  <input type="checkbox" checked={checked} onChange={() => toggleBudgetFocus(option.value)} />
                  <span><strong>{option.label}</strong><small>{option.description}</small></span>
                </label>
              );
            })}
          </div>
          </fieldset>
        </section>
        {error && <p className="profile-message error">{error}</p>}
        {notice && <p className="profile-message success">{notice}</p>}
        <div className="profile-actions">
          <button type="button" className="profile-cancel" onClick={() => { setForm(initial); setError(""); setNotice(""); }}>취소</button>
          <button type="submit" className="profile-save" disabled={!changed || saving}>{saving ? "저장 중..." : "저장하기"}</button>
        </div>
      </form>
    </div>
  );
}

export default ProfileSettingsPanel;
