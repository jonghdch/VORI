import "./RecordCalendar.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** 날짜 → "YYYY-M-D" 키. 기록/강조 집합과 셀을 맞추는 단일 규칙. */
export function dateKey(year, month, day) {
  return `${year}-${month}-${day}`;
}

/**
 * 월간 기록 캘린더 — 가계부 기록이 있는 날을 칠해서 보여주는 7열 숫자 그리드.
 * 홈(이번 달)과 소비 리포트(보고 있는 기간의 달)가 같이 쓴다.
 *
 * @param {number} year
 * @param {number} month                1~12
 * @param {Set<string>|null} recordedKeys  기록 있는 날 키(dateKey) 집합. null = 로딩 중
 * @param {Set<string>} [highlightKeys]    강조할 날 키 집합(리포트에서 보고 있는 기간)
 * @param {boolean} [showAdjacent]         앞뒤 빈 칸을 이웃 달 날짜로 채움(흐리게). 달을 걸치는 주를 끊김 없이 보여줄 때
 * @param {(date:Date)=>void} [onSelectDay] 있으면 셀이 버튼이 되어 클릭 가능
 */
function RecordCalendar({
  year,
  month,
  recordedKeys,
  highlightKeys,
  showAdjacent = false,
  onSelectDay,
}) {
  const today = new Date();
  const todayKey = dateKey(today.getFullYear(), today.getMonth() + 1, today.getDate());
  const daysInMonth = new Date(year, month, 0).getDate();
  const firstWeekday = new Date(year, month - 1, 1).getDay();

  // 그리드 셀 목록 — 이웃 달을 채우면 항상 7의 배수, 아니면 앞만 빈 칸.
  const cells = [];
  for (let i = firstWeekday - 1; i >= 0; i--) {
    cells.push(showAdjacent ? { date: new Date(year, month - 1, -i), adjacent: true } : null);
  }
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push({ date: new Date(year, month - 1, d), adjacent: false });
  }
  if (showAdjacent) {
    let d = 1;
    while (cells.length % 7 !== 0) {
      cells.push({ date: new Date(year, month, d++), adjacent: true });
    }
  }

  return (
    <div
      className="rc-grid"
      role={onSelectDay ? "group" : "img"}
      aria-label={`${year}년 ${month}월 가계부 기록 캘린더`}
    >
      {WEEKDAYS.map((d) => (
        <span key={d} className="rc-weekday">
          {d}
        </span>
      ))}
      {cells.map((cell, i) => {
        if (!cell) return <span key={`blank-${i}`} className="rc-cell rc-cell--blank" />;
        const { date, adjacent } = cell;
        const y = date.getFullYear();
        const m = date.getMonth() + 1;
        const d = date.getDate();
        const key = dateKey(y, m, d);
        const cls = [
          "rc-cell",
          adjacent ? "rc-cell--adjacent" : "",
          recordedKeys?.has(key) ? "is-marked" : "",
          key === todayKey ? "is-today" : "",
          highlightKeys?.has(key) ? "is-highlight" : "",
        ]
          .filter(Boolean)
          .join(" ");
        return onSelectDay ? (
          <button
            key={key}
            type="button"
            className={cls}
            onClick={() => onSelectDay(date)}
            aria-label={`${m}월 ${d}일`}
          >
            {d}
          </button>
        ) : (
          <span key={key} className={cls}>
            {d}
          </span>
        );
      })}
    </div>
  );
}

export default RecordCalendar;
