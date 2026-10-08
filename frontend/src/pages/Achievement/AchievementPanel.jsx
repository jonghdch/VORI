import { useMemo, useState } from "react";
import { EQUIP_LIMIT, equipAchievements } from "../../api/achievements";
import AchievementSlots from "../../components/AchievementSlots";
import "./AchievementPage.css";

function categoryForCode(code) {
  if (code.startsWith("LOGIN_")) return "시작";
  if (code.startsWith("SAVER_")) return "절약";
  if (code.startsWith("RECORD_")) return "기록";
  if (code.startsWith("GOAL_")) return "목표";
  // 펫 칭호 수를 세는 업적 — 칭호는 펫이 얻는다(도감 칭호 탭)
  if (code.startsWith("PET_TITLE_") || code === "PET_ALLROUNDER" || code === "PET_SECRET_FOUND") return "칭호";
  if (code.startsWith("PET_") || code.startsWith("DEX_")) return "펫";
  if (code === "LUCKY") return "가챠";
  if (code === "TALKATIVE") return "AI";
  if (code === "SCAN_MASTER") return "영수증";
  return "기타";
}

const CATEGORY_ORDER = ["시작", "절약", "기록", "목표", "펫", "칭호", "가챠", "AI", "영수증", "기타"];

function formatProgressText(item) {
  const { code, current, threshold, acquired } = item;
  if (acquired) return "달성 완료";
  // 못 딴 히든 업적은 서버가 조건을 가려서 보낸다 — 달성률만
  if (item.hidden) return `${item.progressPct}%`;

  if (code.startsWith("SAVER_")) {
    return `${current.toLocaleString("ko-KR")}원 / ${threshold.toLocaleString("ko-KR")}원`;
  }
  if (code.startsWith("GOAL_")) {
    return `${current}회 / ${threshold}회 달성`;
  }
  if (code.startsWith("RECORD_")) {
    return `${current}건 / ${threshold}건 기록`;
  }
  if (code === "PET_LOVELY") {
    return `${current}회 / ${threshold}회 상호작용`;
  }
  if (code === "PET_NEW_FAMILY") {
    return `${current}마리 / ${threshold}마리 부화`;
  }
  if (code.startsWith("DEX_")) {
    return `${current}종 / ${threshold}종 배웅`;
  }
  if (code.startsWith("PET_TITLE_") || code === "PET_ALLROUNDER" || code === "PET_SECRET_FOUND") {
    return `${current}개 / ${threshold}개`;
  }
  if (code.startsWith("PET_")) {
    return `${current}마리 / ${threshold}마리 배웅`;
  }
  if (code === "LUCKY") {
    return `${current}회 / ${threshold}회 S등급 획득`;
  }
  if (code === "TALKATIVE") {
    return `${current}회 / ${threshold}회 AI 답변`;
  }
  if (code === "SCAN_MASTER") {
    return `${current}회 / ${threshold}회 영수증 인식`;
  }
  if (code.startsWith("LOGIN_")) {
    return `${current}회 / ${threshold}회 로그인`;
  }
  return `${current} / ${threshold}`;
}

/**
 * 도감의 "업적" 탭 — 유저가 쌓는 업적. 목록은 PetDexPage 가 불러 주고, 장착하면 onChange(갱신된 목록).
 * 칭호는 펫이 얻는 것이라 칭호 탭(PetTitlePanel)이 따로 그린다.
 */
function AchievementPanel({ titles, loading, error, onChange }) {
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);

  // 장착한 업적(내 정보 상자에 보이는 3칸) — 순서대로
  const equippedList = useMemo(
    () => titles.filter((t) => t.equipOrder != null).sort((a, b) => a.equipOrder - b.equipOrder),
    [titles],
  );

  const toggleEquip = async (item) => {
    const ids = equippedList.map((t) => t.id);
    const next = item.equipOrder != null ? ids.filter((id) => id !== item.id) : [...ids, item.id];
    setBusy(true);
    setNotice(null);
    try {
      // 장착 응답이 갱신된 목록이다 — 다시 조회하지 않고 바로 반영한다(조회만 실패해 옛 목록으로 다음 장착을 계산하는 일이 없게)
      onChange(await equipAchievements(next));
    } catch (e) {
      setNotice(e.message || "업적을 장착하지 못했어요");
    } finally {
      setBusy(false);
    }
  };

  const groupedAchievements = useMemo(() => {
    const map = new Map();
    for (const t of titles) {
      const cat = categoryForCode(t.code);
      if (!map.has(cat)) map.set(cat, []);
      map.get(cat).push(t);
    }
    return CATEGORY_ORDER.filter((c) => map.has(c)).map((c) => ({
      category: c,
      items: map.get(c),
    }));
  }, [titles]);

  return (
    <>
      {error && (
        <div className="ach-notice ach-notice--err" role="alert">
          {error}
        </div>
      )}
      {notice && (
        <div className="ach-notice ach-notice--err" role="alert">
          {notice}
        </div>
      )}
      {!loading && (
        <>
          <p className="ach-equip-hint">
            업적은 {EQUIP_LIMIT}개까지 장착할 수 있어요 · {equippedList.length} / {EQUIP_LIMIT}
          </p>
          <AchievementSlots
            shown={equippedList}
            disabled={busy}
            onRemove={toggleEquip}
            className="ach-equip-slots"
          />
        </>
      )}

      {loading ? (
        <p className="ach-empty">불러오는 중…</p>
      ) : (
        groupedAchievements.map(({ category, items }) => (
          <section key={category} className="ach-section">
            <h2 className="ach-section-title">{category}</h2>
            <div className="ach-grid">
              {items.map((item) => (
                <article
                  key={item.code}
                  className={`ach-card ${item.acquired ? "is-done" : ""} ${!item.acquired ? "is-locked" : ""}`}
                >
                  <div className="ach-card-top">
                    <h3 className="ach-card-name">{item.description}</h3>
                    <span
                      className={`home-badge ${item.acquired ? "home-badge--done" : "home-badge--prog"}`}
                    >
                      {item.acquired ? "완료" : `${item.progressPct}%`}
                    </span>
                  </div>
                  <p className="ach-card-desc">
                    {item.hidden ? "히든 업적 · " : ""}업적 「{item.name}」
                  </p>
                  <div className="ach-progress-row">
                    <span>달성도</span>
                    <span>{formatProgressText(item)}</span>
                  </div>
                  <div className="ach-progress-track" aria-hidden>
                    <div
                      className={`ach-progress-fill ${item.acquired ? "ach-progress-fill--done" : ""}`}
                      style={{ width: `${item.progressPct}%` }}
                    />
                  </div>
                  {item.acquired && (
                    <button
                      type="button"
                      className={`ach-equip-btn ${item.equipOrder != null ? "is-on" : ""}`}
                      disabled={busy || (item.equipOrder == null && equippedList.length >= EQUIP_LIMIT)}
                      onClick={() => toggleEquip(item)}
                    >
                      {item.equipOrder != null ? "장착 해제" : "장착하기"}
                    </button>
                  )}
                </article>
              ))}
            </div>
          </section>
        ))
      )}
    </>
  );
}

export default AchievementPanel;
