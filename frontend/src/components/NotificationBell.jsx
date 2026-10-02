import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  deleteAllNotifications,
  getUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "../api/notifications";

// 알림 종류별 점 색 — 아이콘 대신 작은 색 점으로 구분한다
const TYPE_TONE = {
  MONTHLY_REPORT: "report",
  JUDGMENT_OPEN: "judgment",
  TITLE_ACQUIRED: "title",
  PET_TITLE_ACQUIRED: "title",
  PET_EVOLVED: "pet",
  PET_GRADUATE_READY: "pet",
};

/** "방금" · "5분 전" · "3시간 전" · "어제" · "9월 28일" */
function timeAgo(iso) {
  const t = new Date(iso);
  if (Number.isNaN(t.getTime())) return "";
  const diff = (Date.now() - t.getTime()) / 1000;
  if (diff < 60) return "방금";
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
  if (diff < 172800) return "어제";
  return `${t.getMonth() + 1}월 ${t.getDate()}일`;
}

/**
 * 헤더 알림 — 종 아이콘(안 읽은 개수) + 누르면 바로 아래 알림 목록.
 * 안 읽은 개수는 처음 뜰 때, 창으로 돌아올 때, 다른 요청이 끝났을 때(vori:account-updated), 1분마다 다시 읽는다.
 * 목록은 열 때만 읽는다. 알림을 누르면 읽음 처리하고 그 화면으로 간다.
 */
function NotificationBell() {
  const navigate = useNavigate();
  const rootRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState(null); // null = 불러오는 중
  const [error, setError] = useState(false);

  const refreshCount = useCallback(() => {
    getUnreadCount()
      .then((r) => setUnread(r?.count ?? 0))
      .catch(() => {});
  }, []);

  useEffect(() => {
    refreshCount();
    const timer = setInterval(refreshCount, 60_000);
    window.addEventListener("focus", refreshCount);
    window.addEventListener("vori:account-updated", refreshCount);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", refreshCount);
      window.removeEventListener("vori:account-updated", refreshCount);
    };
  }, [refreshCount]);

  useEffect(() => {
    if (!open) return undefined;
    let alive = true;
    setItems(null);
    setError(false);
    listNotifications()
      .then((list) => alive && setItems(Array.isArray(list) ? list : []))
      .catch(() => alive && setError(true));
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

  const openItem = async (n) => {
    setOpen(false);
    if (!n.read) {
      setUnread((c) => Math.max(0, c - 1));
      markNotificationRead(n.id).catch(() => {});
    }
    if (n.link) navigate(n.link);
  };

  const removeAll = async () => {
    if (!window.confirm("알림을 모두 지울까요?")) return;
    const before = items;
    setItems([]);
    setUnread(0);
    try {
      await deleteAllNotifications();
    } catch {
      setItems(before); // 실패하면 되돌린다
      refreshCount();
    }
  };

  const readAll = async () => {
    setItems((list) => (list ?? []).map((n) => ({ ...n, read: true })));
    setUnread(0);
    await markAllNotificationsRead().catch(() => {});
  };

  return (
    <div className="noti" ref={rootRef}>
      <button
        type="button"
        className={`noti-bell ${open ? "is-open" : ""}`}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={unread > 0 ? `알림 ${unread}개 안 읽음` : "알림"}
      >
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <path
            d="M6 9a6 6 0 1 1 12 0c0 4.2 1.4 6 2.2 7H3.8C4.6 15 6 13.2 6 9ZM9.8 19.5a2.3 2.3 0 0 0 4.4 0"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        {unread > 0 && <span className="noti-badge">{unread > 9 ? "9+" : unread}</span>}
      </button>

      {open && (
        <div className="noti-popover" role="dialog" aria-label="알림">
          <div className="noti-head">
            <strong>알림</strong>
            {items && items.length > 0 && (
              <div className="noti-actions">
                {items.some((n) => !n.read) && (
                  <button type="button" className="noti-readall" onClick={readAll}>
                    모두 읽음
                  </button>
                )}
                <button type="button" className="noti-readall noti-clear" onClick={removeAll}>
                  전체 삭제
                </button>
              </div>
            )}
          </div>
          {error ? (
            <p className="noti-empty">알림을 불러오지 못했어요.</p>
          ) : items === null ? (
            <p className="noti-empty">불러오는 중…</p>
          ) : items.length === 0 ? (
            <p className="noti-empty">새 알림이 없어요.</p>
          ) : (
            <ul className="noti-list">
              {items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    className={`noti-item ${n.read ? "" : "is-unread"}`}
                    onClick={() => openItem(n)}
                  >
                    <span className={`noti-dot noti-dot--${TYPE_TONE[n.type] ?? "report"}`} aria-hidden />
                    <span className="noti-text">
                      <span className="noti-title">{n.title}</span>
                      {n.body && <span className="noti-body">{n.body}</span>}
                      <span className="noti-time">{timeAgo(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

export default NotificationBell;
