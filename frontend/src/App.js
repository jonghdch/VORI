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
import { getOnboardingStatus } from "./api/onboarding";
import { ADMIN_NAV } from "./pages/Admin/adminNav";

// Story 페이지는 three.js + GLTFLoader 를 포함해서 무거움 (~100+ KB).
// 랜딩만 보는 사용자가 다운로드 안 하도록 별도 chunk 로 분리.
const StoryPage = lazy(() => import("./pages/Story/StoryPage"));
const SignupProfilePage = lazy(() =>
  import("./pages/Signup/SignupProfilePage"),
);
const OnboardingPage = lazy(() => import("./pages/Onboarding/OnboardingPage"));
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
// 소비 리포트 — 아직 플레이스홀더 (/wallet 보이는 리포트의 "자세히보기" 진입점).
const ReportPage = lazy(() => import("./pages/Report/ReportPage"));
const PetPage = lazy(() => import("./pages/Pet/PetPage"));
const PetDexPage = lazy(() => import("./pages/PetDex/PetDexPage"));
const ShopPage = lazy(() => import("./pages/Shop/ShopPage"));
const SettingsPage = lazy(() => import("./pages/Settings/SettingsPage"));
const ProfileSettingsPage = lazy(() =>
  import("./pages/Settings/ProfileSettingsPage"),
);
const AchievementPage = lazy(() => import("./pages/Achievement/AchievementPage"));
// 이용약관·개인정보처리방침 — 공개(비인증) 페이지.
const TermsPage = lazy(() => import("./pages/Legal/TermsPage"));
const PrivacyPage = lazy(() => import("./pages/Legal/PrivacyPage"));
const AdminLayout = lazy(() => import("./pages/Admin/AdminLayout"));
const AdminPetManagePage = lazy(() => import("./pages/Admin/PetManagePage"));
const AdminPlaceholder = lazy(() =>
  import("./pages/Admin/AdminPlaceholder"),
);
const AdminUsersPage = lazy(() => import("./pages/Admin/UsersPage"));
const AdminDashboardPage = lazy(() => import("./pages/Admin/DashboardPage"));
const AdminCategoryStatsPage = lazy(() =>
  import("./pages/Admin/CategoryStatsPage"),
);
const AdminRationalityRulesPage = lazy(() =>
  import("./pages/Admin/RationalityRulesPage"),
);
const AdminAiLogsPage = lazy(() => import("./pages/Admin/AiLogsPage"));
const AdminSanctionsPage = lazy(() => import("./pages/Admin/SanctionsPage"));
const AdminTitleManagePage = lazy(() =>
  import("./pages/Admin/TitleManagePage"),
);

// 실제 화면이 준비된 어드민 메뉴만 매핑. 나머지는 AdminPlaceholder.
const ADMIN_PAGES = {
  "/admin/pets": AdminPetManagePage,
  "/admin/dashboard": AdminDashboardPage,
  "/admin/users": AdminUsersPage,
  "/admin/sanctions": AdminSanctionsPage,
  "/admin/category-stats": AdminCategoryStatsPage,
  "/admin/rationality-rules": AdminRationalityRulesPage,
  "/admin/ai-logs": AdminAiLogsPage,
  "/admin/achievements": AdminTitleManagePage,
};

// 라우터 경로
//   /                       랜딩
//   /login                  로그인
//   /signup                 회원가입
//   /signup/profile         회원가입 후 소비 프로필 5단계
//   /onboarding             가입 직후 온보딩
//   /story                  스토리 (서비스 소개)
//   /terms                  이용약관 (공개)
//   /privacy                개인정보처리방침 (공개)
//   /home                   홈 대시보드 (인증 필요)
//   /wallet                 가계부 달력/조회 (인증 필요)
//   /wallet/new             가계부 작성 Step 1 (입력)
//   /wallet/new/confirm     Step 2 (확인)
//   /wallet/analysis        소비 분석 (오후 8시~자정 이벤트, ledger-ai-card 진입)
//   /report                 소비 리포트 (플레이스홀더)
//   /raise                  펫 키우기
//   /dex                    펫 도감
//   /shop                   상점
//   /settings               환경설정
//   /settings/profile       프로필 설정
//   /titles                 업적/칭호
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

// 설문 필수 — 소비 프로필 설문을 마치지 않은 로그인 계정은 설문 화면(/signup/profile) 밖으로 못 나간다.
// 보호 화면뿐 아니라 첫 화면·스토리·로그인·약관 같은 공개 화면도 막는다. 라우트마다 거는 대신
// <Routes> 전체를 감싸 새 화면이 생겨도 빠지지 않게 한다. 서버도 OnboardingRequiredFilter 로 막는다.
// 관리자는 설문 대상이 아니다. 상태는 계정이 바뀔 때 한 번 읽고, 설문을 마치면 vori:onboarding-done 으로 풀린다.
const SURVEY_PATH = "/signup/profile";
function ProfileRequiredGuard({ user, children }) {
  const { pathname } = useLocation();
  // 결과를 어느 계정 것인지와 함께 둔다. 계정이 바뀐 첫 렌더에 이전 계정의 결과로 화면을 여는 일을 막는다.
  const [status, setStatus] = useState({ userId: null, needsProfile: false, failed: false });
  const [attempt, setAttempt] = useState(0);
  const userId = user?.id ?? null;
  const exempt = !userId || user?.role === "ADMIN";
  useEffect(() => {
    if (exempt) return undefined;
    let alive = true;
    getOnboardingStatus()
      .then((s) => alive && setStatus({ userId, needsProfile: !s?.profileCompleted, failed: false }))
      .catch(() => alive && setStatus({ userId, needsProfile: false, failed: true }));
    const done = () => setStatus({ userId, needsProfile: false, failed: false });
    window.addEventListener("vori:onboarding-done", done);
    return () => {
      alive = false;
      window.removeEventListener("vori:onboarding-done", done);
    };
  }, [userId, exempt, attempt]);
  if (exempt) return children;
  if (status.userId !== userId) return null; // 이 계정의 설문 상태를 아직 모름
  // 상태를 못 읽었으면 열지도(설문 우회), 설문으로 보내지도(설문을 마친 사용자까지 튕김) 않고 다시 묻는다.
  if (status.failed) {
    return (
      <div className="profile-guard-error" role="alert">
        <p>계정 상태를 확인하지 못했어요.</p>
        <button type="button" onClick={() => { setStatus((cur) => ({ ...cur, userId: null })); setAttempt((n) => n + 1); }}>
          다시 시도
        </button>
      </div>
    );
  }
  if (status.needsProfile && pathname !== SURVEY_PATH) return <Navigate to={SURVEY_PATH} replace />;
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
        <ProfileRequiredGuard user={user}>
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
            path="/signup/profile"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <SignupProfilePage onLogout={handleLogout} />
              </ProtectedRoute>
            }
          />
          <Route
            path="/onboarding"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <OnboardingPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/story"
            element={<StoryPage user={user} onLogout={handleLogout} />}
          />
          <Route path="/terms" element={<TermsPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
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
                {/* user 는 작성 중 임시저장(draft)을 계정별로 나누는 데 쓴다.
                    ProtectedRoute 가 user 없이는 렌더하지 않으므로 항상 값이 있다. */}
                <WalletEntryPage user={user} />
              </ProtectedRoute>
            }
          />
          {/* 소비 분석 — 위저드 단계가 아니라 /wallet 의 ledger-ai-card 에서
              오후 8시~자정 이벤트로 진입하는 독립 페이지. */}
          <Route
            path="/wallet/analysis"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <WalletAnalysisPage user={user} />
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
            path="/report"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <ReportPage user={user} onLogout={handleLogout} />
              </ProtectedRoute>
            }
          />
          <Route
            path="/raise"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <PetPage user={user} onLogout={handleLogout} />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dex"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <PetDexPage user={user} onLogout={handleLogout} />
              </ProtectedRoute>
            }
          />
          <Route
            path="/shop"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <ShopPage user={user} onLogout={handleLogout} />
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
          <Route
            path="/settings/profile"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <ProfileSettingsPage
                  user={user}
                  onLogout={handleLogout}
                  onUserUpdate={setUser}
                />
              </ProtectedRoute>
            }
          />
          <Route
            path="/titles"
            element={
              <ProtectedRoute user={user} authLoading={authLoading}>
                <AchievementPage user={user} onLogout={handleLogout} />
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
        </ProfileRequiredGuard>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
