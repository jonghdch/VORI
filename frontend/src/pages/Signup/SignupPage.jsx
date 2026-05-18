import { useState } from "react";
import "./SignupPage.css";
import { signup, login } from "../../api/auth";

// 회원가입 페이지.
// - POST /api/auth/signup 호출 → 성공 시 자동으로 /api/auth/login 까지 호출해
//   세션 쿠키를 받고 랜딩으로 이동.
// - onLogin(user) 으로 App 의 user 상태를 갱신.
// eslint-disable-next-line no-useless-escape
const SPECIAL_CHAR_RE = /[!@#$%^&*()_+\-=\[\]{};:'",.<>/?]/;

function SignupPage({ onNavigate, onLogin }) {
  const [form, setForm] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    nickname: "",
    name: "",
    agreeAll: false,
    agreeTerms: false,
    agreePrivacy: false,
    agreeMarketing: false,
  });
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

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

  // 비밀번호 매칭/길이/특수문자 검증 (백엔드 정책과 동일)
  const pwTooShort = form.password.length > 0 && form.password.length < 8;
  const pwNoSpecial =
    form.password.length > 0 && !SPECIAL_CHAR_RE.test(form.password);
  const pwMismatch =
    form.passwordConfirm.length > 0 && form.password !== form.passwordConfirm;
  const requiredOk = form.agreeTerms && form.agreePrivacy;

  const canSubmit =
    form.email &&
    form.password.length >= 8 &&
    SPECIAL_CHAR_RE.test(form.password) &&
    form.password === form.passwordConfirm &&
    form.nickname &&
    form.name &&
    requiredOk &&
    !loading;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    setError("");
    setLoading(true);
    try {
      await signup({
        email: form.email,
        password: form.password,
        nickname: form.nickname,
        name: form.name,
        termsAgreed: form.agreeTerms,
        privacyAgreed: form.agreePrivacy,
        marketingAgreed: form.agreeMarketing,
      });
      // 가입 성공 → 곧바로 로그인까지 처리해서 세션 쿠키 발급
      const user = await login(form.email, form.password);
      if (typeof onLogin === "function") onLogin(user);
      go("landing");
    } catch (err) {
      setError(err.message || "회원가입 중 오류가 발생했어요");
    } finally {
      setLoading(false);
    }
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
                placeholder="화면에 표시될 이름 (2~12자)"
                value={form.nickname}
                onChange={(e) => set("nickname", e.target.value)}
                maxLength={12}
                required
              />
            </label>

            <label className="signup-field">
              <span className="signup-label">이름</span>
              <input
                type="text"
                className="signup-input"
                placeholder="실명 (한글 또는 영문)"
                value={form.name}
                onChange={(e) => set("name", e.target.value)}
                maxLength={30}
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
              {!pwTooShort && pwNoSpecial && (
                <span className="signup-hint signup-hint-error">
                  특수문자를 1개 이상 포함해 주세요.
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

            {error && (
              <p
                className="signup-hint signup-hint-error"
                role="alert"
                style={{ marginTop: 4 }}
              >
                {error}
              </p>
            )}

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
