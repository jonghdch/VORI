// 공통 상단 헤더.
//
// props:
//   onNavigate(page, sectionId?)  — 페이지 전환
//   transparent (boolean)         — 배경/보더를 투명하게. 어두운 표지 위에 띄울 때 사용
//   minimal (boolean)             — 홈/서비스 소개/이용 방법 메뉴를 숨김. 로고 + 로그인/회원가입만 노출
//   extraNavItems                 — 특정 페이지에서만 추가로 보여줄 우측 메뉴
function SiteHeader({
  onNavigate,
  transparent = false,
  minimal = false,
  extraNavItems = [],
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
          {!minimal && (
            <>
              <button
                type="button"
                className="landing-nav-link landing-nav-link-btn"
                onClick={() => go("landing")}
              >
                홈
              </button>
              <button
                type="button"
                className="landing-nav-link landing-nav-link-btn"
                onClick={() => go("landing", "features")}
              >
                서비스 소개
              </button>
              <button
                type="button"
                className="landing-nav-link landing-nav-link-btn"
                onClick={() => go("landing", "how")}
              >
                이용 방법
              </button>
            </>
          )}
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
        </nav>
      </div>
    </header>
  );
}

export default SiteHeader;
