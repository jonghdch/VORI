import { useState } from "react";
import "./SignupPage.css";

// 회원가입 페이지 (프론트엔드만).
// - 백엔드 연동은 아직 X. 제출하면 잠깐 로딩 표시 후 안내됩니다.
// - 다른 페이지로 이동하려면 onNavigate("login") 등을 호출하세요.
function SignupPage({ onNavigate }) {
  const [form, setForm] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    nickname: "",
    agreeAll: false,
    agreeTerms: false,
    agreePrivacy: false,
    agreeMarketing: false,
  });
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);

  const go = (page) => {
    if (typeof onNavigate === "function") onNavigate(page);
  };

  const set = (key, value) => setForm((f) => ({ ...f, [key]: value }));

  // "전체 동의" 체크 → 하위 약관 모두 동기화
  const toggleAgreeAll = (checked) => {
    setForm((f) => ({
      ...f,
      agreeAll: checked,
      agreeTerms: checked,
      agreePrivacy: checked,
      agreeMarketing: checked,
    }));
  };

  // 개별 약관 체크 시 "전체 동의" 자동 동기화
  const toggleAgreeOne = (key, checked) => {
    setForm((f) => {
      const next = { ...f, [key]: checked };
      next.agreeAll =
        next.agreeTerms && next.agreePrivacy && next.agreeMarketing;
      return next;
    });
  };

  // 비밀번호 매칭/길이 간단 검증
  const pwTooShort = form.password.length > 0 && form.password.length < 8;
  const pwMismatch =
    form.passwordConfirm.length > 0 && form.password !== form.passwordConfirm;
  const requiredOk = form.agreeTerms && form.agreePrivacy;

  const canSubmit =
    form.email &&
    form.password.length >= 8 &&
    form.password === form.passwordConfirm &&
    form.nickname &&
    requiredOk &&
    !loading;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      alert("회원가입 API는 아직 연결 전이에요. (프론트만 구현 상태)");
    }, 600);
  };

  return (
    <div className="signup">
      {/* ───────── 상단 헤더 ───────── */}
      <header className="signup-header">
        <button
          type="button"
          className="signup-logo-btn"
          onClick={() => go("landing")}
          aria-label="VORI 홈으로"
        >
          VORI
        </button>
      </header>

      {/* ───────── 카드 ───────── */}
      <main className="signup-main">
        <section className="signup-card">
          <div className="signup-card-head">
            <h1 className="signup-title">회원가입</h1>
          </div>

          <form className="signup-form" onSubmit={handleSubmit} noValidate>
            <label className="signup-field">
              <span className="signup-label">닉네임</span>
              <input
                type="text"
                className="signup-input"
                placeholder="이름 (2~12자)"
                value={form.nickname}
                onChange={(e) => set("nickname", e.target.value)}
                maxLength={12}
                required
              />
            </label>

            <label className="signup-field">
              <span className="signup-label">이메일</span>
              <input
                type="email"
                className="signup-input"
                placeholder="vori@example.com"
                value={form.email}
                onChange={(e) => set("email", e.target.value)}
                autoComplete="email"
                required
              />
            </label>

            <label className="signup-field">
              <span className="signup-label">비밀번호</span>
              <div className="signup-password-wrap">
                <input
                  type={showPw ? "text" : "password"}
                  className="signup-input"
                  placeholder="8자 이상 입력하세요"
                  value={form.password}
                  onChange={(e) => set("password", e.target.value)}
                  autoComplete="new-password"
                  required
                />
                <button
                  type="button"
                  className="signup-eye"
                  onClick={() => setShowPw((v) => !v)}
                  aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 보기"}
                >
                  {showPw ? "숨기기" : "보기"}
                </button>
              </div>
              {pwTooShort && (
                <span className="signup-hint signup-hint-error">
                  비밀번호는 8자 이상이어야 해요.
                </span>
              )}
            </label>

            <label className="signup-field">
              <span className="signup-label">비밀번호 확인</span>
              <input
                type={showPw ? "text" : "password"}
                className="signup-input"
                placeholder="비밀번호를 한 번 더 입력하세요"
                value={form.passwordConfirm}
                onChange={(e) => set("passwordConfirm", e.target.value)}
                autoComplete="new-password"
                required
              />
              {pwMismatch && (
                <span className="signup-hint signup-hint-error">
                  비밀번호가 일치하지 않아요.
                </span>
              )}
            </label>

            {/* ───────── 약관 동의 ───────── */}
            <div className="signup-agree">
              <label className="signup-agree-row signup-agree-all">
                <input
                  type="checkbox"
                  checked={form.agreeAll}
                  onChange={(e) => toggleAgreeAll(e.target.checked)}
                />
                <span>전체 동의</span>
              </label>
              <div className="signup-agree-divider" />
              <label className="signup-agree-row">
                <input
                  type="checkbox"
                  checked={form.agreeTerms}
                  onChange={(e) => toggleAgreeOne("agreeTerms", e.target.checked)}
                />
                <span>
                  <em className="signup-required">(필수)</em> 이용약관 동의
                </span>
              </label>
              <label className="signup-agree-row">
                <input
                  type="checkbox"
                  checked={form.agreePrivacy}
                  onChange={(e) =>
                    toggleAgreeOne("agreePrivacy", e.target.checked)
                  }
                />
                <span>
                  <em className="signup-required">(필수)</em> 개인정보 수집·이용 동의
                </span>
              </label>
              <label className="signup-agree-row">
                <input
                  type="checkbox"
                  checked={form.agreeMarketing}
                  onChange={(e) =>
                    toggleAgreeOne("agreeMarketing", e.target.checked)
                  }
                />
                <span>
                  <em className="signup-optional">(선택)</em> 마케팅 정보 수신 동의
                </span>
              </label>
            </div>

            <button
              type="submit"
              className="signup-submit"
              disabled={!canSubmit}
            >
              {loading ? "가입 처리 중…" : "회원가입"}
            </button>
          </form>

          {/* ───────── 로그인 안내 ───────── */}
          <p className="signup-login">
            이미 계정이 있으신가요?{" "}
            <button
              type="button"
              className="signup-link signup-link-strong"
              onClick={() => go("login")}
            >
              로그인
            </button>
          </p>
        </section>
      </main>

      <footer className="signup-footer">졸업작품 © 2026 VORI Team</footer>
    </div>
  );
}

export default SignupPage;
