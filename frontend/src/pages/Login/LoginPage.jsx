import { useState } from "react";
import { useNavigate } from "react-router-dom";
import "./LoginPage.css";
import { login } from "../../api/auth";
import GoogleSignInButton from "../../components/GoogleSignInButton";
import { getOnboardingStatus } from "../../api/onboarding";

// 로그인 페이지.
// - POST /api/auth/login 호출, 세션 쿠키(JSESSIONID)로 인증 유지.
// - 성공 시 온보딩 상태를 보고 이어 할 화면으로 이동.
function LoginPage({ onLogin }) {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  // 로그인은 됐지만 온보딩 상태를 못 읽은 계정 — "다시 시도" 가 상태만 다시 묻는다.
  const [pendingUser, setPendingUser] = useState(null);

  // 로그인 뒤 갈 곳 — 이메일·구글 로그인이 같이 쓴다. 설문 전이면 설문, 튜토리얼 전이면 온보딩.
  //
  // 상태를 못 읽었으면 홈으로 보내지 않는다. 못 읽은 것을 "다 마쳤다" 로 치면 온보딩을 안 한
  // 계정이 그대로 홈에 들어간다. 로그인 상태(onLogin)도 상태를 읽은 뒤에 올린다 — 먼저 올리면
  // 설문 가드(App.js)가 화면을 다시 그려 이 화면의 안내가 사라진다.
  const afterLogin = async (user) => {
    let status;
    try {
      status = await getOnboardingStatus();
    } catch {
      setPendingUser(user);
      setError("로그인은 됐지만 계정 상태를 확인하지 못했어요.");
      return;
    }
    setPendingUser(null);
    if (typeof onLogin === "function") onLogin(user);
    if (!status?.profileCompleted) {
      navigate("/signup/profile");
    } else if (!status.tutorialDone) {
      navigate("/onboarding");
    } else {
      navigate("/home");
    }
  };

  const retryStatus = async () => {
    setError("");
    setLoading(true);
    try {
      await afterLogin(pendingUser);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setPendingUser(null);
    setLoading(true);
    try {
      await afterLogin(await login(email, password));
    } catch (err) {
      setError(err.message || "로그인 중 오류가 발생했어요");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login">
      {/* ───────── 상단 헤더 (로고 클릭하면 랜딩으로) ───────── */}
      <header className="login-header">
        <button
          type="button"
          className="login-logo-btn"
          onClick={() => navigate("/")}
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

            {error && (
              <p
                className="login-error"
                role="alert"
                style={{ color: "#c0392b", margin: "4px 0 0", fontSize: "0.9rem" }}
              >
                {error}
                {pendingUser && (
                  <>
                    {" "}
                    <button
                      type="button"
                      className="login-link"
                      onClick={retryStatus}
                      disabled={loading}
                    >
                      다시 시도
                    </button>
                  </>
                )}
              </p>
            )}

            <button
              type="submit"
              className="login-submit"
              disabled={loading}
            >
              {loading ? "로그인 중…" : "로그인"}
            </button>
          </form>

          {/* 구글 로그인 — REACT_APP_GOOGLE_CLIENT_ID 가 있을 때만 버튼이 그려진다 */}
          {process.env.REACT_APP_GOOGLE_CLIENT_ID && (
            <>
              <div className="login-divider">또는</div>
              <GoogleSignInButton
                onLogin={afterLogin}
                onError={(msg) => {
                  setPendingUser(null);
                  setError(msg);
                }}
              />
            </>
          )}

          {/* ───────── 회원가입 안내 ───────── */}
          <p className="login-signup">
            아직 계정이 없으신가요?{" "}
            <button
              type="button"
              className="login-link login-link-strong"
              onClick={() => navigate("/signup")}
            >
              회원가입
            </button>
          </p>
        </section>
      </main>

      <footer className="login-footer">
        <div className="login-footer-legal">
          <a href="/terms" target="_blank" rel="noopener noreferrer">
            이용약관
          </a>
          <span aria-hidden>·</span>
          <a href="/privacy" target="_blank" rel="noopener noreferrer">
            개인정보처리방침
          </a>
        </div>
        <span>졸업작품 © 2026 VORI Team</span>
      </footer>
    </div>
  );
}

export default LoginPage;
