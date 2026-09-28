import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ME_CHANGED_EVENT, getMe } from "../api/user";

const coin = (n) => (n ?? 0).toLocaleString("ko-KR");

/**
 * 헤더 우측 — 보유 코인 캡슐 + 원형 프로필. 프로필을 누르면 바로 아래에 사용자 정보 팝오버.
 *
 * 세션의 user 객체가 아니라 GET /api/users/me 를 직접 부른다 — 코인·누적 절약은 알 구매·지출
 * 등록으로 계속 바뀌는데 세션 값은 로그인 시점 스냅샷이라서다. 같은 화면 안에서 코인이 바뀌면
 * (상점 구매·분양) 호출부가 ME_CHANGED_EVENT 를 쏘고 여기서 다시 읽는다.
 *
 * 프로필 사진 필드가 아직 없어 닉네임 첫 글자로 아바타를 만든다.
 */
function UserMenu({ me: meProp, onLogout }) {
  const navigate = useNavigate();
  const [me, setMe] = useState(meProp ?? null);
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);

  const reload = useCallback(() => {
    getMe()
      .then(setMe)
      .catch(() => {}); // 401 등은 상위 라우트 가드가 처리한다
  }, []);

  // 부모(AppShell)가 me 를 주면 그걸 그대로 쓴다. 단독으로 쓰일 때만 직접 읽고 변경 이벤트를 듣는다.
  useEffect(() => {
    if (meProp) return undefined;
    reload();
    window.addEventListener(ME_CHANGED_EVENT, reload);
    return () => window.removeEventListener(ME_CHANGED_EVENT, reload);
  }, [meProp, reload]);

  useEffect(() => {
    if (meProp) setMe(meProp);
  }, [meProp]);

  // 바깥 클릭·Esc 로 닫기
  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const nickname = me?.nickname || "사용자";
  const initial = nickname.trim().charAt(0).toUpperCase() || "V";
  const isAdmin = me?.role === "ADMIN";

  return (
    <div className="user-menu" ref={rootRef}>
      <button
        type="button"
        className="user-menu-coins"
        onClick={() => navigate("/shop")}
        title="상점으로"
        aria-label={`보유 코인 ${coin(me?.gameMoney)}`}
      >
        <span className="user-menu-coin-icon" aria-hidden>
          ●
        </span>
        <span className="user-menu-coin-value">{me ? coin(me.gameMoney) : "…"}</span>
      </button>

      <button
        type="button"
        className={`user-menu-avatar ${isAdmin ? "is-admin" : ""} ${open ? "is-open" : ""}`}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`${nickname} 프로필 메뉴`}
      >
        {initial}
      </button>

      {open && (
        <div className="user-menu-popover" role="dialog" aria-label="내 정보">
          <div className="user-menu-head">
            <span className={`user-menu-avatar user-menu-avatar--lg ${isAdmin ? "is-admin" : ""}`} aria-hidden>
              {initial}
            </span>
            <div className="user-menu-who">
              <strong>
                {nickname}
                {isAdmin && <em className="user-menu-role">관리자</em>}
              </strong>
              <small>{me?.email ?? ""}</small>
            </div>
          </div>

          <dl className="user-menu-stats">
            <div>
              <dt>보유 코인</dt>
              <dd>{coin(me?.gameMoney)}</dd>
            </div>
            <div>
              <dt>누적 절약</dt>
              <dd>{coin(me?.totalSaved)}원</dd>
            </div>
          </dl>

          <div className="user-menu-actions">
            <button
              type="button"
              className="home-btn home-btn-primary"
              onClick={() => {
                setOpen(false);
                navigate("/settings");
              }}
            >
              프로필 수정
            </button>
            {isAdmin && (
              <button
                type="button"
                className="home-btn"
                onClick={() => {
                  setOpen(false);
                  navigate("/admin");
                }}
              >
                관리자 페이지
              </button>
            )}
            <button
              type="button"
              className="home-link-btn user-menu-logout"
              onClick={() => {
                setOpen(false);
                if (typeof onLogout === "function") onLogout();
              }}
            >
              로그아웃
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default UserMenu;
