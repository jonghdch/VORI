import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

// 로그인된 사용자 아바타 + 드롭다운 (설정 / 로그아웃).
// SiteHeader, HomeDashboard 등 헤더에서 공통 사용.
function UserAvatarMenu({ user, onLogout }) {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const initial =
    (user?.nickname || user?.email || "?").trim().charAt(0).toUpperCase();

  const handleLogout = async () => {
    setOpen(false);
    if (typeof onLogout === "function") await onLogout();
    navigate("/");
  };

  return (
    <div className="user-menu" ref={wrapRef}>
      <button
        type="button"
        className="user-avatar"
        aria-label="내 계정 메뉴"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        {initial}
      </button>

      {open && (
        <div className="user-popover" role="menu">
          <div className="user-popover-row">
            <div className="user-popover-photo" aria-hidden>
              {initial}
            </div>
            <div className="user-popover-info">
              <div className="user-popover-nickname">{user?.nickname}</div>
              <div className="user-popover-email">{user?.email}</div>
            </div>
          </div>
          <button
            type="button"
            className="user-popover-item"
            onClick={() => {
              setOpen(false);
              navigate("/settings");
            }}
          >
            환경설정
          </button>
          <button
            type="button"
            className="user-popover-item user-popover-logout"
            onClick={handleLogout}
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}

export default UserAvatarMenu;
