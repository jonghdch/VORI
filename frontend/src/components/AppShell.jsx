import { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../pages/Home/HomeDashboard.css";

const TOP_NAV = [
  { id: "home", label: "홈" },
  { id: "wallet", label: "가계부" },
  { id: "shop", label: "상점" },
  { id: "raise", label: "마이룸" },
];

// page: null = 아직 화면이 없는 메뉴 — 누르면 아무 일도 없는 척하지 않도록
// disabled + "준비 중" 표기로 렌더한다.
const SIDE_MENU = [
  { id: "home", label: "홈 대시보드", page: "home" },
  { id: "wallet", label: "가계부", page: "wallet" },
  { id: "report", label: "소비 리포트", page: "report" },
];

const GAME_MENU = [
  { id: "raise", label: "마이룸", page: "raise" },
  { id: "dex", label: "펫 도감", page: "dex" },
  { id: "shop", label: "상점", page: "shop" },
  { id: "achievement", label: "업적/칭호", page: "titles" },
];

// 데스크톱(1025px 이상)에서 왼쪽 사이드바를 접어 둔 상태를 기억한다. 모바일 드로어와는 별개.
const SIDEBAR_COLLAPSED_KEY = "vori.sidebar.collapsed";
const MOBILE_QUERY = "(max-width: 1024px)";

function readSidebarCollapsed() {
  try {
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "1";
  } catch {
    return false;
  }
}

function AppShell({
  activeTop = "home",
  activeSide = "home",
  onNavigate,
  onLogout,
  children,
}) {
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false); // 모바일: 슬라이드 드로어
  const [collapsed, setCollapsed] = useState(readSidebarCollapsed); // 데스크톱: 사이드바 접힘

  const isMobile = () =>
    typeof window !== "undefined" && window.matchMedia(MOBILE_QUERY).matches;

  const toggleSidebar = () => {
    if (isMobile()) {
      setMenuOpen((v) => !v);
      return;
    }
    setCollapsed((v) => {
      const next = !v;
      try {
        localStorage.setItem(SIDEBAR_COLLAPSED_KEY, next ? "1" : "0");
      } catch {}
      return next;
    });
  };

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
              aria-label={menuOpen || !collapsed ? "메뉴 닫기" : "메뉴 열기"}
              aria-expanded={isMobile() ? menuOpen : !collapsed}
              onClick={toggleSidebar}
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

      <div className={`home-shell ${collapsed ? "home-shell--collapsed" : ""}`}>
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
