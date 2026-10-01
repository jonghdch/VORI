import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { checkInAttendance, getAttendance, getAttendanceMonth } from "../api/attendance";
import "./AttendanceModal.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const STREAK_GOAL = 3; // 이 일수마다 확정 보상 아이템을 준다

const pad = (n) => String(n).padStart(2, "0");
const monthKeyOf = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
const dateKeyOf = (date) => `${monthKeyOf(date)}-${pad(date.getDate())}`;

/** 출석 팝업을 열라는 신호. 계정 메뉴의 "출석 확인" 이 쏘고 AttendanceGate 가 듣는다. */
export const OPEN_ATTENDANCE_EVENT = "vori:open-attendance";
export const openAttendance = () => window.dispatchEvent(new Event(OPEN_ATTENDANCE_EVENT));

// 오늘 이미 팝업을 보여 줬는지(닫았는지) — 자동으로는 하루 한 번만 띄운다.
const SHOWN_KEY = "vori.attendance.shownOn";
export const wasShownToday = () => {
  try {
    return localStorage.getItem(SHOWN_KEY) === dateKeyOf(new Date());
  } catch {
    return false;
  }
};
export const markShownToday = () => {
  try {
    localStorage.setItem(SHOWN_KEY, dateKeyOf(new Date()));
  } catch {}
};

/**
 * 출석 팝업 — 원래 /attendance 페이지였던 내용을 그대로 담은 다이얼로그.
 * 출석하기 · 연속 출석 띠 · 달력 · 이번 달 보상. 닫기 버튼·Esc·바깥 클릭으로 닫힌다.
 */
function AttendanceModal({ onClose }) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const today = useMemo(() => new Date(), []);
  const currentMonth = monthKeyOf(today);
  const todayKey = dateKeyOf(today);

  const [month, setMonth] = useState(currentMonth);
  const [status, setStatus] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [checkingIn, setCheckingIn] = useState(false);
  const [notice, setNotice] = useState(null); // { kind: "ok" | "err", text }

  const loadMonth = useCallback(async (targetMonth) => {
    const [nextStatus, nextHistory] = await Promise.all([
      getAttendance(),
      getAttendanceMonth(targetMonth),
    ]);
    setStatus(nextStatus);
    setHistory(nextHistory || []);
  }, []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    loadMonth(month)
      .catch((e) => {
        if (alive) setNotice({ kind: "err", text: e.message });
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [month, loadMonth]);

  // 팝업이 떠 있는 동안 뒤 화면 스크롤 잠금 + Esc 로 닫기 + 첫 포커스
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    dialogRef.current?.focus();
    return () => {
      document.body.style.overflow = prev;
      document.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  const handleCheckIn = async () => {
    setCheckingIn(true);
    setNotice(null);
    try {
      const result = await checkInAttendance();
      setStatus(result);
      setNotice({
        kind: "ok",
        text: result.awardedItem
          ? `${result.awardedItem.name}을(를) 받았어요!`
          : "출석 완료! 내일 다시 도전해 보세요.",
      });
      if (month === currentMonth) await loadMonth(month);
    } catch (e) {
      setNotice({ kind: "err", text: e.message });
    } finally {
      setCheckingIn(false);
    }
  };

  const [year, mon] = month.split("-").map(Number);
  const firstWeekday = new Date(year, mon - 1, 1).getDay();
  const daysInMonth = new Date(year, mon, 0).getDate();

  const byDate = useMemo(
    () => Object.fromEntries(history.map((item) => [item.date, item])),
    [history],
  );
  const cells = useMemo(
    () => [
      ...Array(firstWeekday).fill(null),
      ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
    ],
    [firstWeekday, daysInMonth],
  );

  const changeMonth = (delta) => {
    const next = new Date(year, mon - 1 + delta, 1);
    if (next > new Date(today.getFullYear(), today.getMonth(), 1)) return;
    setMonth(monthKeyOf(next));
  };

  const streak = status?.streakCount ?? 0;
  const cycleProgress = streak % STREAK_GOAL;
  const daysToReward = STREAK_GOAL - cycleProgress;
  const ready = !loading || !!status;

  return (
    <div
      className="attendance-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className="attendance-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <button type="button" className="attendance-close" onClick={onClose} aria-label="닫기">
          ×
        </button>

        <div className="attendance-head">
          <div>
            <h1 className="attendance-title" id={titleId}>
              <span className="attendance-title-badge" aria-hidden>
                🐾
              </span>
              출석
            </h1>
            <p className="attendance-subtitle">
              {STREAK_GOAL}일 연속 출석마다 확정 스탯 +10 아이템을 받아요.
            </p>
          </div>
          <button
            type="button"
            className={`attendance-cta ${
              status?.checkedIn ? "attendance-cta--done" : "attendance-cta--ready"
            }`}
            disabled={checkingIn || loading || status?.checkedIn}
            onClick={handleCheckIn}
          >
            <span className="attendance-cta-icon" aria-hidden>
              {checkingIn ? "⏳" : status?.checkedIn ? "✅" : "🐾"}
            </span>
            {loading && !status
              ? "불러오는 중…"
              : checkingIn
                ? "처리 중…"
                : status?.checkedIn
                  ? "오늘 출석 완료"
                  : "오늘 출석하기"}
          </button>
        </div>

        {notice && (
          <p
            className={`attendance-notice ${notice.kind === "err" ? "attendance-notice--err" : ""}`}
            role={notice.kind === "err" ? "alert" : "status"}
          >
            {notice.text}
          </p>
        )}

        <section className="attendance-banner">
          <div className="attendance-banner-streak">
            <span className="attendance-banner-flame" aria-hidden>
              🔥
            </span>
            <div>
              <strong className="attendance-banner-count">{ready ? streak : "…"}</strong>
              <span className="attendance-banner-count-label">일 연속 출석</span>
            </div>
          </div>

          <div className="attendance-banner-divider" aria-hidden />

          <div className="attendance-banner-progress">
            <div className="attendance-banner-dots" aria-hidden>
              {Array.from({ length: STREAK_GOAL }, (_, i) => (
                <span
                  key={i}
                  className={`attendance-banner-dot ${i < cycleProgress ? "is-filled" : ""}`}
                />
              ))}
              <span className="attendance-banner-gift">🎁</span>
            </div>
            <p className="attendance-banner-progress-text">
              {!ready
                ? "불러오는 중…"
                : cycleProgress === 0
                  ? `오늘부터 ${STREAK_GOAL}일을 채우면 보상이에요`
                  : `보상까지 ${daysToReward}일 남았어요`}
            </p>
          </div>

          <div className="attendance-banner-divider" aria-hidden />

          <div
            className={`attendance-banner-today ${
              status?.checkedIn ? (status.itemAwarded ? "is-reward" : "is-done") : ""
            }`}
          >
            <span aria-hidden>{status?.checkedIn ? (status.itemAwarded ? "🎉" : "✅") : "🕗"}</span>
            {!ready
              ? "확인 중…"
              : status?.checkedIn
                ? status.itemAwarded
                  ? "오늘 아이템 획득"
                  : "오늘 출석 완료"
                : "아직 출석 전"}
          </div>
        </section>

        <section className="attendance-calendar-card">
          <div className="attendance-cal-head">
            <button
              type="button"
              className="attendance-cal-nav"
              onClick={() => changeMonth(-1)}
              aria-label="이전 달"
            >
              ‹
            </button>
            <div className="attendance-cal-title">
              <h2>
                <span aria-hidden>🐾</span> {year}년 {mon}월
              </h2>
              {month !== currentMonth && (
                <button
                  type="button"
                  className="attendance-cal-today"
                  onClick={() => setMonth(currentMonth)}
                >
                  오늘로
                </button>
              )}
            </div>
            <button
              type="button"
              className="attendance-cal-nav"
              onClick={() => changeMonth(1)}
              disabled={month === currentMonth}
              aria-label="다음 달"
            >
              ›
            </button>
          </div>

          <div className="attendance-weekdays" aria-hidden>
            {WEEKDAYS.map((d, i) => (
              <span key={d} className={i === 0 ? "is-sun" : i === 6 ? "is-sat" : ""}>
                {d}
              </span>
            ))}
          </div>

          <div className="attendance-grid">
            {cells.map((day, i) => {
              if (!day) return <span key={`empty-${i}`} className="attendance-day--empty" />;

              const weekdayIdx = i % 7;
              const dateKey = `${month}-${pad(day)}`;
              const item = byDate[dateKey];
              const isToday = dateKey === todayKey;
              const isStreakBonus = !!item?.streakBonus;
              const title = item
                ? item.itemAwarded
                  ? `${item.rewardName} +${item.rewardStatDelta}`
                  : "출석 완료"
                : undefined;

              return (
                <div
                  key={dateKey}
                  className={`attendance-day ${weekdayIdx === 0 ? "is-sun" : ""} ${
                    weekdayIdx === 6 ? "is-sat" : ""
                  } ${item ? "is-checked" : ""} ${isToday ? "is-today" : ""}`}
                  title={title}
                >
                  <span className="attendance-day-num">{day}</span>
                  {isStreakBonus && (
                    <span className="attendance-day-star" aria-hidden>
                      ★
                    </span>
                  )}
                  {item && !isStreakBonus && (
                    <span className="attendance-day-check" aria-hidden>
                      🐾
                    </span>
                  )}
                </div>
              );
            })}
          </div>

          <ul className="attendance-legend">
            <li>
              <span className="attendance-legend-dot attendance-legend-dot--checked" aria-hidden>
                🐾
              </span>
              출석한 날
            </li>
            <li>
              <span className="attendance-legend-dot attendance-legend-dot--streak">★</span>
              {STREAK_GOAL}일 연속 보상
            </li>
            <li>
              <span className="attendance-legend-dot attendance-legend-dot--today" />
              오늘
            </li>
          </ul>
        </section>

      </div>
    </div>
  );
}

export default AttendanceModal;
