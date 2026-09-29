import { useEffect, useMemo, useState } from "react";
import AppShell from "../../components/AppShell";
import { checkInAttendance, getAttendance, getAttendanceMonth } from "../../api/attendance";
import "./AttendancePage.css";

const STAT = { ENERGY: "에너지", CHARM: "매력", IQ: "지능", ENDURANCE: "지구력" };
const pad = (n) => String(n).padStart(2, "0");

export default function AttendancePage({ user, onLogout }) {
  const today = useMemo(() => new Date(), []);
  const [month, setMonth] = useState(() => `${today.getFullYear()}-${pad(today.getMonth() + 1)}`);
  const [status, setStatus] = useState(null);
  const [history, setHistory] = useState([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);
  const load = () => Promise.all([getAttendance(), getAttendanceMonth(month)]).then(([nextStatus, nextHistory]) => { setStatus(nextStatus); setHistory(nextHistory || []); });
  useEffect(() => { load().catch((e) => setNotice({ kind: "err", text: e.message })); }, [month]); // eslint-disable-line react-hooks/exhaustive-deps
  const attend = async () => { setBusy(true); try { const result = await checkInAttendance(); setStatus(result); setNotice({ kind: "ok", text: result.awardedItem ? `${result.awardedItem.name}을(를) 받았어요!` : "출석 완료! 내일 다시 도전해 보세요." }); if (month === `${today.getFullYear()}-${pad(today.getMonth() + 1)}`) await load(); } catch (e) { setNotice({ kind: "err", text: e.message }); } finally { setBusy(false); } };
  const [year, mon] = month.split("-").map(Number);
  const first = new Date(year, mon - 1, 1).getDay();
  const days = new Date(year, mon, 0).getDate();
  const byDate = Object.fromEntries(history.map((item) => [item.date, item]));
  const cells = [...Array(first).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)];
  const change = (delta) => { const d = new Date(year, mon - 1 + delta, 1); if (d > new Date(today.getFullYear(), today.getMonth(), 1)) return; setMonth(`${d.getFullYear()}-${pad(d.getMonth() + 1)}`); };
  return <AppShell activeTop="home" activeSide="attendance" user={user} onLogout={onLogout}><main className="home-main attendance-main" style={{ maxWidth: "none", width: "100%" }}>
    <header className="attendance-head"><div><p>게임</p><h1>출석</h1><span>3일 연속 출석마다 확정 스탯 +10 아이템을 받아요.</span></div><button type="button" className="home-btn home-btn-primary" disabled={busy || status?.checkedIn} onClick={attend}>{busy ? "처리 중…" : status?.checkedIn ? "오늘 출석 완료" : "오늘 출석하기"}</button></header>
    {notice && <p className={`attendance-notice ${notice.kind}`}>{notice.text}</p>}
    <section className="attendance-summary"><div><span>현재 연속 출석</span><strong>{status?.streakCount ?? 0}일</strong></div><div><span>다음 확정 보상</span><strong>{Math.max(0, 3 - ((status?.streakCount ?? 0) % 3))}일 후</strong></div><div><span>오늘 보상</span><strong>{status?.checkedIn ? (status.itemAwarded ? "아이템 획득" : "미획득") : "출석 전"}</strong></div></section>
    <section className="attendance-calendar-card"><div className="attendance-cal-head"><button onClick={() => change(-1)}>‹</button><h2>{year}년 {mon}월</h2><button onClick={() => change(1)} disabled={month === `${today.getFullYear()}-${pad(today.getMonth() + 1)}`}>›</button></div><div className="attendance-week">{["일","월","화","수","목","금","토"].map((d) => <span key={d}>{d}</span>)}</div><div className="attendance-grid">{cells.map((day, i) => { if (!day) return <span key={`e${i}`} />; const date = `${month}-${pad(day)}`; const item = byDate[date]; return <article key={date} className={`attendance-day ${item ? "is-checked" : ""} ${item?.streakBonus ? "is-streak" : ""}`}><strong>{day}</strong>{item && <small>{item.itemAwarded ? `${item.rewardName} +${item.rewardStatDelta}` : "출석 완료"}</small>}</article>; })}</div></section>
    <p className="attendance-help">초록: 출석 완료 · 금색 테두리: 3일 연속 확정 보상 · 아이템은 마이룸의 아이템 탭에서 사용할 수 있어요.</p>
  </main></AppShell>;
}
