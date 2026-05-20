import { useEffect, useRef, useState } from "react";

// 공통 상단 헤더.
//
// props:
//   onNavigate(page, sectionId?)  — 페이지 전환
//   transparent (boolean)         — 배경/보더를 투명하게. 어두운 표지 위에 띄울 때 사용
//   extraNavItems                 — 특정 페이지에서만 추가로 보여줄 우측 메뉴
//   user                          — 로그인된 사용자 { id, email, nickname, role } 또는 null
//   onLogout()                    — 로그아웃 핸들러 (App 에서 주입)
function SiteHeader({
  onNavigate,
  transparent = false,
  extraNavItems = [],
  user = null,
  onLogout,
}) {
  const go = (page, section) => {
    if (typeof onNavigate === "function") onNavigate(page, section);
  };

  const headerCls = [
    "landing-header",
    transparent ? "landing-header--transparent" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <header className={headerCls}>
      <div className="landing-header-inner">
        <div className="landing-header-left">
          <button
            type="button"
            className="landing-logo landing-logo-btn"
            onClick={() => go("landing")}
            aria-label="VORI 메인으로"
          >
            VORI
          </button>
          <button
            type="button"
            className="landing-nav-link landing-nav-link-btn"
            onClick={() => go("story")}
          >
            스토리
          </button>
        </div>
        <nav className="landing-nav">
          {extraNavItems.map((item) => (
            <button
              key={item.label}
              type="button"
              className="landing-nav-link landing-nav-link-btn"
              onClick={item.onClick}
            >
              {item.label}
            </button>
          ))}

          {user ? (
            <UserMenu user={user} onNavigate={onNavigate} onLogout={onLogout} />
          ) : (
            <>
              <button
                type="button"
                className="landing-btn landing-btn-ghost"
                onClick={() => go("login")}
              >
                로그인
              </button>
              <button
                type="button"
                className="landing-btn landing-btn-primary"
                onClick={() => go("signup")}
              >
                회원가입
              </button>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}

// 로그인된 사용자 영역.
// - "VORI 시작하기" 버튼 + 원형 아바타.
// - 아바타 클릭 시 드롭다운 팝업: 프로필 사진, 닉네임, 이메일, 로그아웃.
function UserMenu({ user, onNavigate, onLogout }) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  // 바깥 클릭 시 닫기
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

  return (
    <>
      <button
        type="button"
        className="landing-btn landing-btn-primary"
        onClick={() => {
          if (typeof onNavigate === "function") onNavigate("home");
        }}
      >
        VORI 시작하기
      </button>

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
                <div className="user-popover-nickname">{user.nickname}</div>
                <div className="user-popover-email">{user.email}</div>
              </div>
            </div>
            <button
              type="button"
              className="user-popover-logout"
              onClick={() => {
                setOpen(false);
                if (typeof onLogout === "function") onLogout();
              }}
            >
              로그아웃
            </button>
          </div>
        )}
      </div>
    </>
  );
}

export default SiteHeader;
