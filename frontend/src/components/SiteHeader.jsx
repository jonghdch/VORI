import { useNavigate } from "react-router-dom";
import UserAvatarMenu from "./UserAvatarMenu";

// 공통 상단 헤더.
//
// props:
//   transparent (boolean)         — 배경/보더를 투명하게. 어두운 표지 위에 띄울 때 사용
//   extraNavItems                 — 특정 페이지에서만 추가로 보여줄 우측 메뉴
//   user                          — 로그인된 사용자 { id, email, nickname, role } 또는 null
//   onLogout()                    — 로그아웃 핸들러 (App 에서 주입). user state 만 정리하고,
//                                   네비게이션은 헤더가 navigate("/") 로 직접 처리
function SiteHeader({
  transparent = false,
  extraNavItems = [],
  user = null,
  onLogout,
}) {
  const navigate = useNavigate();

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
            onClick={() => navigate("/")}
            aria-label="VORI 메인으로"
          >
            VORI
          </button>
          <button
            type="button"
            className="landing-nav-link landing-nav-link-btn"
            onClick={() => navigate("/story")}
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
            <UserMenu user={user} onLogout={onLogout} />
          ) : (
            <>
              <button
                type="button"
                className="landing-btn landing-btn-ghost"
                onClick={() => navigate("/login")}
              >
                로그인
              </button>
              <button
                type="button"
                className="landing-btn landing-btn-primary"
                onClick={() => navigate("/signup")}
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
// - "VORI 시작하기" 버튼 + 공통 UserAvatarMenu (설정/로그아웃 드롭다운).
function UserMenu({ user, onLogout }) {
  const navigate = useNavigate();
  return (
    <>
      <button
        type="button"
        className="landing-btn landing-btn-primary"
        onClick={() => navigate("/home")}
      >
        VORI 시작하기
      </button>
      <UserAvatarMenu user={user} onLogout={onLogout} />
    </>
  );
}

export default SiteHeader;
