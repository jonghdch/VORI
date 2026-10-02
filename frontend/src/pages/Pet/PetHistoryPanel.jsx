import { useEffect, useState } from "react";
import { PetArt, STAGE_LABEL, VARIANT_LABEL, petDisplayName } from "../../components/petVisual";
import { STAT_LABEL } from "../../components/furnitureVisual";
import { getAttendanceMonth } from "../../api/attendance";

// 마이룸 "내역 보기" — 펫 이력과 아이템(출석 보상) 내역을 서브 탭으로 나눠 본다.
const SUB_TABS = [
  { id: "pets", label: "펫" },
  { id: "items", label: "아이템" },
];

const formatDate = (iso) => (iso ? iso.slice(0, 10).replaceAll("-", ". ") : "");
const coin = (n) => `${(n ?? 0).toLocaleString("ko-KR")} 코인`;
const monthKeyOf = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;

function PetHistoryPanel({ petHistory }) {
  const [view, setView] = useState("pets");

  return (
    <div className="pet-tab-panel">
      <div className="pet-panel-head">
        <h2 className="home-card-title home-card-title--sm">내역</h2>
      </div>
      <div className="pet-subtab-bar" role="tablist" aria-label="내역 종류">
        {SUB_TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`pet-history-tab-${tab.id}`}
            aria-selected={view === tab.id}
            aria-controls={view === tab.id ? `pet-history-panel-${tab.id}` : undefined}
            className={`pet-subtab-btn ${view === tab.id ? "is-active" : ""}`}
            onClick={() => setView(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div id={`pet-history-panel-${view}`} role="tabpanel" aria-labelledby={`pet-history-tab-${view}`}>
        {view === "pets" ? <PetHistoryList petHistory={petHistory} /> : <ItemHistoryList />}
      </div>
    </div>
  );
}

function PetHistoryList({ petHistory }) {
  if (petHistory.length === 0) return <p className="pet-empty">아직 함께한 펫이 없어요.</p>;
  return (
    <>
      <p className="pet-history-count">{petHistory.length}마리</p>
      <ul className="pet-history-list">
        {petHistory.map((p) => (
          <li key={p.id} className={`pet-history-item ${p.releasedAt ? "is-released" : ""}`}>
            <span className="pet-history-art">
              <PetArt
                appearanceKey={p.appearanceKey}
                stage={p.stage}
                name={p.speciesName}
                className="pet-history-image"
                emojiClassName="pet-history-emoji"
              />
            </span>
            <div className="pet-history-info">
              <strong>{petDisplayName(p)}</strong>
              <small>
                {[p.name ? p.speciesName : null, STAGE_LABEL[p.stage], VARIANT_LABEL[p.variant]]
                  .filter(Boolean)
                  .join(" · ")}
              </small>
            </div>
            <span className="pet-history-state">
              {p.releasedAt ? `${formatDate(p.releasedAt)} 분양 · ${coin(p.releaseValue)}` : "키우는 중"}
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}

// 아이템 내역 — 출석으로 받은 보상. 서버에 월 단위로만 남아 있어(GET /attendance/month) 달을 넘겨 가며 본다.
function ItemHistoryList() {
  const thisMonth = monthKeyOf(new Date());
  const [month, setMonth] = useState(thisMonth);
  const [state, setState] = useState({ month: null, rewards: [], error: "" });

  useEffect(() => {
    let alive = true;
    getAttendanceMonth(month)
      .then((days) => {
        if (!alive) return;
        const rewards = (days || [])
          .filter((d) => d.itemAwarded)
          .sort((a, b) => b.date.localeCompare(a.date));
        setState({ month, rewards, error: "" });
      })
      .catch((e) => alive && setState({ month, rewards: [], error: e.message || "내역을 불러오지 못했어요." }));
    return () => {
      alive = false;
    };
  }, [month]);

  const moveMonth = (delta) => {
    const [y, m] = month.split("-").map(Number);
    const next = monthKeyOf(new Date(y, m - 1 + delta, 1));
    if (next <= thisMonth) setMonth(next);
  };
  const [year, mon] = month.split("-").map(Number);
  const loading = state.month !== month;

  return (
    <>
      <div className="pet-history-month">
        <button type="button" onClick={() => moveMonth(-1)} aria-label="이전 달">‹</button>
        <span>{year}년 {mon}월</span>
        <button type="button" onClick={() => moveMonth(1)} disabled={month >= thisMonth} aria-label="다음 달">›</button>
      </div>
      {loading ? (
        <p className="pet-empty" role="status">불러오는 중…</p>
      ) : state.error ? (
        <p className="pet-empty" role="alert">{state.error}</p>
      ) : state.rewards.length === 0 ? (
        <p className="pet-empty">이 달에 받은 아이템이 없어요.</p>
      ) : (
        <ul className="pet-history-list">
          {state.rewards.map((r) => (
            <li key={r.date} className="pet-history-item pet-history-item--reward">
              <div className="pet-history-info">
                <strong>{r.rewardName}</strong>
                <small>{STAT_LABEL[r.rewardStatType] ?? "스탯"} +{r.rewardStatDelta}{r.streakBonus ? " · 연속 출석 보상" : ""}</small>
              </div>
              <span className="pet-history-state">{formatDate(r.date)} 출석</span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

export default PetHistoryPanel;
