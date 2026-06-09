import { useNavigate } from "react-router-dom";
import "../pages/Home/HomeDashboard.css";

const TOP_NAV = [
  { id: "home", label: "홈" },
  { id: "wallet", label: "가계부" },
  { id: "shop", label: "상점" },
  { id: "raise", label: "키우기" },
];

const SIDE_MENU = [
  { id: "home", label: "홈 대시보드", page: "home" },
  { id: "wallet", label: "가계부", page: "wallet" },
  { id: "report", label: "소비 리포트", page: null },
];

const GAME_MENU = [
  { id: "raise", label: "펫 키우기", page: "raise" },
  { id: "shop", label: "상점", page: "shop" },
  { id: "achievement", label: "업적/칭호", page: null },
];

function AppShell({
  activeTop = "home",
  activeSide = "home",
  onNavigate,
  onLogout,
  children,
}) {
  const navigate = useNavigate();

  const go = (page) => {
    if (!page) return;
    if (typeof onNavigate === "function") {
      onNavigate(page);
      return;
    }
    navigate(page.startsWith("/") ? page : `/${page}`);
  };

  return (
    <div className="home">
      <header className="home-topbar">
        <div className="home-topbar-inner">
          <button
            type="button"
            className="home-logo"
            onClick={() => go("home")}
            aria-label="VORI 홈"
          >
            <span className="home-logo-vo">VO</span>
            <span className="home-logo-ri">RI</span>
          </button>
          <nav className="home-topnav" aria-label="주 메뉴">
            {TOP_NAV.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`home-topnav-item ${item.id === activeTop ? "is-active" : ""}`}
                onClick={() => {
                  if (
                    item.id === "home" ||
                    item.id === "wallet" ||
                    item.id === "raise" ||
                    item.id === "shop"
                  ) {
                    go(item.id);
                  }
                }}
              >
                {item.label}
              </button>
            ))}
          </nav>
          <button
            type="button"
            className="home-logout-link"
            onClick={() => {
              if (typeof onLogout === "function") onLogout();
            }}
          >
            로그아웃
          </button>
        </div>
      </header>

      <div className="home-shell">
        <aside
          className="home-sidebar home-sidebar--left"
          aria-label="사이드 메뉴"
        >
          <div className="home-side-block">
            <div className="home-side-title">메뉴</div>
            <ul className="home-side-list">
              {SIDE_MENU.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={`home-side-link ${item.id === activeSide ? "is-active" : ""}`}
                    onClick={() => go(item.page)}
                  >
                    {item.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>
          <div className="home-side-block">
            <div className="home-side-title">게임</div>
            <ul className="home-side-list">
              {GAME_MENU.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={`home-side-link ${item.id === activeSide ? "is-active" : ""}`}
                    onClick={() => go(item.page)}
                  >
                    {item.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>
          <div className="home-side-block">
            <div className="home-side-title">설정</div>
            <ul className="home-side-list">
              <li>
                <button type="button" className="home-side-link">
                  환경설정
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="home-side-link"
                  onClick={() => {
                    if (typeof onLogout === "function") onLogout();
                  }}
                >
                  로그아웃
                </button>
              </li>
            </ul>
          </div>
        </aside>

        {children}
      </div>
    </div>
  );
}

export default AppShell;
