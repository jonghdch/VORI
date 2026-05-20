import { lazy, Suspense, useEffect, useState } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  useLocation,
} from "react-router-dom";
import "./App.css";
import LandingPage from "./pages/Landing/LandingPage";
import LoginPage from "./pages/Login/LoginPage";
import SignupPage from "./pages/Signup/SignupPage";
import HomeDashboard from "./pages/Home/HomeDashboard";
import { me, logout } from "./api/auth";

// Story 페이지는 three.js + GLTFLoader 를 포함해서 무거움 (~100+ KB).
// 랜딩만 보는 사용자가 다운로드 안 하도록 별도 chunk 로 분리.
const StoryPage = lazy(() => import("./pages/Story/StoryPage"));

// 라우터 경로
//   /        랜딩
//   /login   로그인
//   /signup  회원가입
//   /story   스토리 (서비스 소개)
//   /home    홈 대시보드 (인증 필요 — 미인증 시 /login 으로 리다이렉트)

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "auto" });
  }, [pathname]);
  return null;
}

// 보호 라우트 — 미인증 사용자는 /login 으로 보냄.
// 첫 me() 호출 끝날 때까지는 화면 깜빡임 방지 위해 아무것도 렌더 X.
function ProtectedRoute({ user, authLoading, children }) {
  if (authLoading) return null;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

function App() {
  const [user, setUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);

  // 새로고침 등으로 진입했을 때 세션 쿠키가 살아있으면 자동 복원.
  useEffect(() => {
    me()
      .then((u) => setUser(u))
      .catch(() => setUser(null))
      .finally(() => setAuthLoading(false));
  }, []);

  const handleLogin = (u) => setUser(u);

  const handleLogout = async () => {
    try {
      await logout();
    } finally {
      setUser(null);
    }
  };

  return (
    <BrowserRouter>
      <ScrollToTop />
      <Suspense fallback={null}>
        <Routes>
          <Route
            path="/"
            element={<LandingPage user={user} onLogout={handleLogout} />}
          />
          <Route
            path="/login"
            element={<LoginPage onLogin={handleLogin} />}
          />
          <Route
            path="/signup"
            element={<SignupPage onLogin={handleLogin} />}
          />
          <Route
            path="/story"
            element={<StoryPage user={user} onLogout={handleLogout} />}
          />
          <Route
            path="/home"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <HomeDashboard user={user} onLogout={handleLogout} />
              </ProtectedRoute>
            }
          />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
