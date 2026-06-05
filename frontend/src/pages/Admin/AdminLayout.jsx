import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { ADMIN_NAV } from "./adminNav";
import "./AdminLayout.css";

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
          {ADMIN_NAV.map((group) => (
            <div key={group.label} className="admin-nav-group">
              <div className="admin-nav-group-label">{group.label}</div>
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
