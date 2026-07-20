import AppShell from "../../components/AppShell";
import AppRightSidebar from "../../components/AppRightSidebar";
import "../Home/HomeDashboard.css";

// 소비 리포트 — 아직 준비 중. /wallet 보이는 리포트의 "자세히보기"가 이곳으로 온다.
// 실제 리포트 화면이 만들어지면 이 플레이스홀더를 교체.
function ReportPage({ user, onLogout }) {
  return (
    <AppShell activeTop="wallet" activeSide="report" user={user} onLogout={onLogout}>
      <main className="home-main">
        <section className="home-card report-placeholder">
          <h1 className="home-card-title">소비 리포트</h1>
          <p className="report-placeholder-text">
            소비 리포트 기능을 준비 중이에요. 곧 이 자리에서 소비 패턴 분석을 볼 수 있어요.
          </p>
        </section>
      </main>
      <AppRightSidebar />
    </AppShell>
  );
}
export default ReportPage;
