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
import { ADMIN_NAV } from "./pages/Admin/adminNav";

// Story 페이지는 three.js + GLTFLoader 를 포함해서 무거움 (~100+ KB).
// 랜딩만 보는 사용자가 다운로드 안 하도록 별도 chunk 로 분리.
const StoryPage = lazy(() => import("./pages/Story/StoryPage"));
const WalletEntryPage = lazy(() =>
  import("./pages/WalletEntry/WalletEntryPage"),
);
const WalletAnalysisPage = lazy(() =>
  import("./pages/WalletEntry/WalletAnalysisPage"),
);
const WalletConfirmPage = lazy(() =>
  import("./pages/WalletEntry/WalletConfirmPage"),
);
// 가계부 달력/조회 (가은 PR #4). AppShell 기반.
const WalletPage = lazy(() => import("./pages/Wallet/WalletPage"));
const SettingsPage = lazy(() => import("./pages/Settings/SettingsPage"));
const AdminLayout = lazy(() => import("./pages/Admin/AdminLayout"));
const AdminPlaceholder = lazy(() =>
  import("./pages/Admin/AdminPlaceholder"),
);
const AdminUsersPage = lazy(() => import("./pages/Admin/UsersPage"));
const AdminDashboardPage = lazy(() => import("./pages/Admin/DashboardPage"));

// 실제 화면이 준비된 어드민 메뉴만 매핑. 나머지는 AdminPlaceholder.
const ADMIN_PAGES = {
  "/admin/dashboard": AdminDashboardPage,
  "/admin/users": AdminUsersPage,
};

// 라우터 경로
//   /                       랜딩
//   /login                  로그인
//   /signup                 회원가입
//   /story                  스토리 (서비스 소개)
//   /home                   홈 대시보드 (인증 필요)
//   /wallet                 가계부 달력/조회 (인증 필요)
//   /wallet/new             가계부 작성 Step 1 (입력)
//   /wallet/new/analysis    Step 2 (AI 사유 질문)
//   /wallet/new/confirm     Step 3 (확인)
//   /settings               환경설정
//   /admin/*                어드민 (ADMIN 전용)

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

// 어드민 라우트 — 관리자(role === "ADMIN") 만 허용.
// 미인증은 /login, 로그인했지만 일반 사용자면 /home 으로 보냄.
function AdminRoute({ user, authLoading, children }) {
  if (authLoading) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "ADMIN") return <Navigate to="/home" replace />;
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
          <Route
            path="/wallet"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <WalletPage user={user} onLogout={handleLogout} />
              </ProtectedRoute>
            }
          />
          <Route
            path="/wallet/new"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <WalletEntryPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/wallet/new/analysis"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <WalletAnalysisPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/wallet/new/confirm"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <WalletConfirmPage user={user} />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <SettingsPage user={user} onLogout={handleLogout} />
              </ProtectedRoute>
            }
          />
          {/* 어드민 — 셸(AdminLayout) + 사이드바 메뉴별 중첩 라우트.
              본문은 현재 AdminPlaceholder. 기본 진입은 종합 대시보드. */}
          <Route
            path="/admin"
            element={
              <AdminRoute user={user} authLoading={authLoading}>
                <AdminLayout />
              </AdminRoute>
            }
          >
            <Route index element={<Navigate to="/admin/dashboard" replace />} />
            {ADMIN_NAV.flatMap((group) =>
              group.items.map((item) => {
                const Page = ADMIN_PAGES[item.to] || AdminPlaceholder;
                return (
                  <Route
                    key={item.to}
                    path={item.to.replace("/admin/", "")}
                    element={<Page />}
                  />
                );
              }),
            )}
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
