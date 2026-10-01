import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import AccountSummary from "./AccountSummary";
import NotificationBell from "./NotificationBell";
import { getMe } from "../api/user";
import { listTitles } from "../api/titles";
import { openAttendance } from "./AttendanceModal";

const coin = (n) => (n ?? 0).toLocaleString("ko-KR");

/**
 * 헤더 계정 영역 — AccountSummary(칭호·닉네임·코인)를 그대로 쓰고, 누르면 그 바로 아래에
 * 내 정보 상자를 띄운다. 상자에서 프로필 수정(→ /settings/profile)·관리자 페이지·로그아웃.
 *
 * 헤더 표시 자체는 AccountSummary 가 맡고, 이 컴포넌트는 열림/닫힘과 상자 내용만 담당한다.
 * 상자 데이터는 열릴 때만 읽는다 — 헤더가 이미 읽은 것과 같은 API 지만, 열 때 최신 값을 보여주는 편이
 * 닫힌 채로 매번 두 번 부르는 것보다 낫다.
 */
function AccountMenu({ onLogout }) {
  const navigate = useNavigate();
  const rootRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [me, setMe] = useState(null);
  const [title, setTitle] = useState(null);

  useEffect(() => {
    if (!open) return undefined;
    let alive = true;
    Promise.all([getMe(), listTitles().catch(() => [])])
      .then(([user, titles]) => {
        if (!alive) return;
        setMe(user);
        const active = Array.isArray(titles) ? titles.find((t) => t.active) : titles?.active;
        setTitle(active?.name || "칭호 없음");
      })
      .catch(() => {});

    const onDown = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      alive = false;
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const isAdmin = me?.role === "ADMIN";
  const nickname = me?.nickname || "사용자";
  const initial = nickname.trim().charAt(0).toUpperCase() || "V";
  const coinText = isAdmin ? "∞" : coin(me?.gameMoney);

  const go = (path) => {
    setOpen(false);
    navigate(path);
  };

  return (
    <div className="account-menu" ref={rootRef}>
      <AccountSummary onClick={() => setOpen((v) => !v)} />
      <NotificationBell />

      {open && (
        <div className="account-menu-popover" role="dialog" aria-label="내 정보">
          <div className="account-menu-head">
            <span className={`account-menu-avatar ${isAdmin ? "is-admin" : ""}`} aria-hidden>
              {initial}
            </span>
            <div className="account-menu-who">
              <strong>
                {nickname}
                {isAdmin && <em className="account-menu-role">관리자</em>}
              </strong>
              <small>{me?.email ?? ""}</small>
              {title && (
                <button
                  type="button"
                  className="account-menu-title"
                  onClick={() => go("/dex?tab=titles")}
                  aria-label={`칭호 ${title} — 칭호 도감 열기`}
                >
                  🏅 {title}
                </button>
              )}
            </div>
          </div>

          <dl className="account-menu-stats">
            <div>
              <dt>보유 코인</dt>
              <dd>{me ? coinText : "…"}</dd>
            </div>
            <div>
              <dt>누적 절약</dt>
              <dd>{me ? `${coin(me.totalSaved)}원` : "…"}</dd>
            </div>
          </dl>

          <div className="account-menu-actions">
            <button type="button" className="home-btn home-btn-primary" onClick={() => go("/settings/profile")}>
              프로필 수정
            </button>
            {isAdmin && (
              <button type="button" className="home-btn" onClick={() => go("/admin")}>
                관리자 페이지
              </button>
            )}
            <button
              type="button"
              className="home-btn"
              onClick={() => {
                setOpen(false);
                openAttendance(); // 출석 팝업을 다시 띄운다 (AttendanceGate)
              }}
            >
              🐾 출석 확인
            </button>
            <button
              type="button"
              className="home-link-btn account-menu-logout"
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

export default AccountMenu;
