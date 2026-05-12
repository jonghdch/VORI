import { useState } from "react";
import "./LoginPage.css";

// 로그인 페이지 (프론트엔드만).
// - 백엔드 연동은 아직 X. 폼 submit 하면 잠깐 로딩 표시만 됩니다.
// - 다른 페이지로 이동할 때는 onNavigate("landing") 처럼 호출하세요.
function LoginPage({ onNavigate }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);

  const go = (page) => {
    if (typeof onNavigate === "function") onNavigate(page);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // 백엔드 붙기 전 임시 동작: 0.6초 로딩 표시 후 안내.
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      alert("로그인 API는 아직 연결 전이에요. (프론트만 구현 상태)");
    }, 600);
  };

  return (
    <div className="login">
      {/* ───────── 상단 헤더 (로고 클릭하면 랜딩으로) ───────── */}
      <header className="login-header">
        <button
          type="button"
          className="login-logo-btn"
          onClick={() => go("landing")}
          aria-label="VORI 홈으로"
        >
          VORI
        </button>
      </header>

      {/* ───────── 카드 ───────── */}
      <main className="login-main">
        <section className="login-card">
          <div className="login-card-head">
            <h1 className="login-title">로그인</h1>
          </div>

          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <label className="login-field">
              <span className="login-label">이메일</span>
              <input
                type="email"
                className="login-input"
                placeholder="vori@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </label>

            <label className="login-field">
              <span className="login-label">비밀번호</span>
              <div className="login-password-wrap">
                <input
                  type={showPw ? "text" : "password"}
                  className="login-input"
                  placeholder="비밀번호를 입력하세요"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className="login-eye"
                  onClick={() => setShowPw((v) => !v)}
                  aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 보기"}
                >
                  {showPw ? "숨기기" : "보기"}
                </button>
              </div>
            </label>

            <div className="login-row-between">
              <label className="login-check">
                <input type="checkbox" />
                <span>로그인 상태 유지</span>
              </label>
              <button
                type="button"
                className="login-link"
                onClick={() =>
                  alert("비밀번호 찾기 화면은 다음에 만들 예정이에요.")
                }
              >
                비밀번호를 잊으셨나요?
              </button>
            </div>

            <button
              type="submit"
              className="login-submit"
              disabled={loading}
            >
              {loading ? "로그인 중…" : "로그인"}
            </button>
          </form>

          {/* ───────── 구분선 ───────── */}
          <div className="login-divider">
            <span>또는</span>
          </div>

          {/* ───────── 소셜 로그인 (UI만) ───────── */}
          <div className="login-socials">
            <button type="button" className="login-social login-social-google">
              <span className="login-social-icon" aria-hidden>G</span>
              Google로 시작하기
            </button>
          </div>

          {/* ───────── 회원가입 안내 ───────── */}
          <p className="login-signup">
            아직 계정이 없으신가요?{" "}
            <button
              type="button"
              className="login-link login-link-strong"
              onClick={() => go("signup")}
            >
              회원가입
            </button>
          </p>
        </section>
      </main>

      <footer className="login-footer">
        졸업작품 © 2026 VORI Team
      </footer>
    </div>
  );
}

export default LoginPage;
