import { Navigate, useNavigate, useParams } from "react-router-dom";
import AppShell from "../../components/AppShell";
import ProfileSettingsPanel from "./ProfileSettingsPanel";
import GeneralSettingsPanel from "./GeneralSettingsPanel";
import "./SettingsPage.css";

// 가계부 입력(WalletEntryPage)이 기본 결제수단을 여기서 읽는다.
export { loadUserSettings } from "./GeneralSettingsPanel";

// 환경설정 — 왼쪽 사이드바 탭으로 항목을 고르고 오른쪽에 그 항목을 보여 준다.
// 탭은 URL(/settings/:tab)에 담아 새로고침·뒤로가기·외부 링크(/settings/profile)가 그대로 동작하게 한다.
const TABS = [
  { id: "profile", label: "프로필", desc: "내 정보를 수정하면 VORI 화면에 바로 반영됩니다." },
  { id: "general", label: "기본 설정", desc: "가계부 입력에 쓰이는 기본값을 정합니다." },
];

function SettingsPage({ user, onLogout, onUserUpdate }) {
  const navigate = useNavigate();
  const { tab } = useParams();
  const current = TABS.find((t) => t.id === tab);
  if (!current) return <Navigate to={`/settings/${TABS[0].id}`} replace />;

  return (
    <AppShell activeTop="" activeSide="settings" onLogout={onLogout}>
      <main className="settings-main home-main">
        <h1 className="settings-title">환경설정</h1>
        <div className="settings-layout">
          <nav className="settings-nav" aria-label="환경설정 항목">
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                className={`settings-nav-btn ${t.id === current.id ? "is-active" : ""}`}
                aria-current={t.id === current.id ? "page" : undefined}
                onClick={() => navigate(`/settings/${t.id}`)}
              >
                {t.label}
              </button>
            ))}
          </nav>
          <section className="settings-panel" aria-labelledby="settings-panel-heading">
            <div className="settings-panel-head">
              <h2 id="settings-panel-heading">{current.label}</h2>
              <p>{current.desc}</p>
            </div>
            {current.id === "profile" && (
              <ProfileSettingsPanel user={user} onUserUpdate={onUserUpdate} />
            )}
            {current.id === "general" && <GeneralSettingsPanel />}
          </section>
        </div>
      </main>
    </AppShell>
  );
}

export default SettingsPage;
