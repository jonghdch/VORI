import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { ADMIN_NAV } from "./adminNav";
import "./AdminLayout.css";

// 기본 관리 그룹 링크 앞 라인 아이콘 (route → SVG).
const svg = (children) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.7"
    strokeLinecap="round"
    strokeLinejoin="round"
    width="18"
    height="18"
    aria-hidden
  >
    {children}
  </svg>
);
const NAV_ICONS = {
  "/admin/dashboard": svg(
    <>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </>,
  ),
  "/admin/users": svg(
    <>
      <circle cx="9" cy="7.5" r="3.3" />
      <path d="M2.8 20a6.2 6.2 0 0 1 12.4 0" />
      <path d="M16 4.6a3.3 3.3 0 0 1 0 6" />
      <path d="M21.2 20a6 6 0 0 0-4-5.4" />
    </>,
  ),
  "/admin/items": svg(
    <>
      <path d="M3.5 8h17v3h-17z" />
      <path d="M5 11v9h14v-9" />
      <path d="M12 8v12" />
      <path d="M12 8C12 5.5 10.2 4.2 8.8 4.9 7.3 5.7 8.3 8 12 8" />
      <path d="M12 8C12 5.5 13.8 4.2 15.2 4.9 16.7 5.7 15.7 8 12 8" />
    </>,
  ),
  "/admin/sanctions": svg(
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M6 6l12 12" />
    </>,
  ),
};

// 어드민 공통 셸. 상단 바(VORI ADMIN / 사용자 페이지) + 좌측 사이드바 + 본문 Outlet.
// 본문은 중첩 라우트가 채운다 (App.js 의 /admin/* 참고).
function AdminLayout() {
  const navigate = useNavigate();

  return (
    <div className="admin">
      <header className="admin-topbar">
        <button
          type="button"
          className="admin-logo"
          onClick={() => navigate("/admin")}
          aria-label="VORI ADMIN 홈으로"
        >
          <span className="admin-logo-vori">VORI</span>
          <span className="admin-logo-admin">ADMIN</span>
        </button>
        <button
          type="button"
          className="admin-topbar-link"
          onClick={() => navigate("/home")}
        >
          사용자 페이지 →
        </button>
      </header>

      <div className="admin-body">
        <nav className="admin-sidebar" aria-label="어드민 메뉴">
          {ADMIN_NAV.map((group, idx) => (
            <div
              key={group.label}
              className={
                idx === 0
                  ? "admin-nav-group admin-nav-group--primary"
                  : "admin-nav-group"
              }
            >
              {/* 첫 그룹(기본 관리)은 라벨 없이 상단에, 아래에 구분선 */}
              {idx !== 0 && (
                <div className="admin-nav-group-label">{group.label}</div>
              )}
              <ul className="admin-nav-list">
                {group.items.map((item) => (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      className={({ isActive }) =>
                        isActive
                          ? "admin-nav-link admin-nav-link--active"
                          : "admin-nav-link"
                      }
                    >
                      {idx === 0 && NAV_ICONS[item.to] && (
                        <span className="admin-nav-icon">
                          {NAV_ICONS[item.to]}
                        </span>
                      )}
                      {item.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        <main className="admin-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default AdminLayout;
