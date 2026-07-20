import { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../pages/Home/HomeDashboard.css";

const TOP_NAV = [
  { id: "home", label: "홈" },
  { id: "wallet", label: "가계부" },
  { id: "shop", label: "상점" },
  { id: "raise", label: "키우기" },
];

// page: null = 아직 화면이 없는 메뉴 — 누르면 아무 일도 없는 척하지 않도록
// disabled + "준비 중" 표기로 렌더한다.
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
  const [menuOpen, setMenuOpen] = useState(false);

  const go = (page) => {
    if (!page) return;
    setMenuOpen(false);
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
          <div className="home-topbar-left">
            <button
              type="button"
              className="home-menu-toggle"
              aria-label={menuOpen ? "메뉴 닫기" : "메뉴 열기"}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((v) => !v)}
            >
              <span />
              <span />
              <span />
            </button>
            <button
              type="button"
              className="home-logo"
              onClick={() => go("home")}
              aria-label="VORI 홈"
            >
              <span className="home-logo-vo">VO</span>
              <span className="home-logo-ri">RI</span>
            </button>
          </div>
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
        </div>
      </header>

      <div className="home-shell">
        {menuOpen && (
          <div
            className="home-sidebar-backdrop"
            onClick={() => setMenuOpen(false)}
            aria-hidden
          />
        )}
        <aside
          className={`home-sidebar home-sidebar--left ${menuOpen ? "is-open" : ""}`}
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
                    disabled={!item.page}
                  >
                    {item.label}
                    {!item.page && (
                      <span className="home-side-soon">준비 중</span>
                    )}
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
                    disabled={!item.page}
                  >
                    {item.label}
                    {!item.page && (
                      <span className="home-side-soon">준비 중</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          </div>
          <div className="home-side-block">
            <div className="home-side-title">설정</div>
            <ul className="home-side-list">
              <li>
                <button
                  type="button"
                  className="home-side-link"
                  onClick={() => go("settings")}
                >
                  환경설정
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="home-side-link"
                  onClick={() => {
                    setMenuOpen(false);
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
