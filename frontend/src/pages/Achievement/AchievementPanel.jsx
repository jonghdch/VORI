import { useMemo, useState } from "react";
import { setActiveTitle } from "../../api/titles";
import "./AchievementPage.css";

function categoryForCode(code) {
  if (code.startsWith("LOGIN_")) return "시작";
  if (code.startsWith("SAVER_")) return "절약";
  if (code.startsWith("RECORD_")) return "기록";
  if (code.startsWith("GOAL_")) return "목표";
  if (code.startsWith("PET_")) return "펫";
  if (code === "LUCKY") return "가챠";
  if (code === "TALKATIVE") return "AI";
  if (code === "SCAN_MASTER") return "영수증";
  return "기타";
}

const CATEGORY_ORDER = ["시작", "절약", "기록", "목표", "펫", "가챠", "AI", "영수증", "기타"];

function formatProgressText(item) {
  const { code, current, threshold, acquired } = item;
  if (acquired) return "달성 완료";

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
  if (code.startsWith("PET_")) {
    return `${current}마리 / ${threshold}마리 분양`;
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

function formatAcquiredAt(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
}

/**
 * 도감의 "업적"·"칭호" 탭 — 같은 칭호 목록을 두 관점으로 보여 준다.
 * tab: "achievements" | "titles". 목록(titles)은 PetDexPage 가 불러 주고, 장착을 바꾸면 reload 로 다시 받는다.
 */
function AchievementPanel({ tab, titles, loading, error, reload }) {
  const [notice, setNotice] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const activeTitle = useMemo(
    () => titles.find((t) => t.active) || null,
    [titles],
  );

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

  const acquiredTitles = useMemo(
    () => titles.filter((t) => t.acquired),
    [titles],
  );

  const handleEquip = async (titleId) => {
    setNotice(null);
    setBusyId(titleId);
    try {
      await setActiveTitle(titleId);
      await reload();
      setNotice({ kind: "ok", text: "칭호를 장착했어요. 홈 화면에서 확인할 수 있어요." });
    } catch (e) {
      setNotice({
        kind: "err",
        text: e.message || "칭호를 장착하지 못했어요",
      });
    } finally {
      setBusyId(null);
    }
  };

  const handleUnequip = async () => {
    setNotice(null);
    setBusyId("none");
    try {
      await setActiveTitle(null);
      await reload();
      setNotice({ kind: "ok", text: "칭호 장착을 해제했어요." });
    } catch (e) {
      setNotice({
        kind: "err",
        text: e.message || "장착 해제에 실패했어요",
      });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      {notice && (
        <div className={`ach-notice ach-notice--${notice.kind}`} role="status">
          {notice.text}
        </div>
      )}

      {error && (
        <div className="ach-notice ach-notice--err" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <p className="ach-empty">불러오는 중…</p>
      ) : tab === "achievements" ? (
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
                    {item.hidden ? "히든 업적 · " : "달성 시 "}칭호 「{item.name}」 획득
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
                </article>
              ))}
            </div>
          </section>
        ))
      ) : (
        <>
          <div className="ach-equipped">
            <div>
              <p className="ach-equipped-label">현재 장착</p>
              <p className="ach-equipped-name">
                {activeTitle ? activeTitle.name : "장착된 칭호 없음"}
              </p>
            </div>
            {activeTitle && (
              <div className="ach-title-actions">
                <button
                  type="button"
                  className="ach-btn-sm ach-btn-ghost"
                  disabled={busyId !== null}
                  onClick={handleUnequip}
                >
                  장착 해제
                </button>
              </div>
            )}
          </div>

          <section className="ach-section">
            <h2 className="ach-section-title">획득한 칭호</h2>
            {acquiredTitles.length === 0 ? (
              <p className="ach-empty">
                아직 획득한 칭호가 없어요. 업적 탭에서 진행 중인 목표를 확인해 보세요.
              </p>
            ) : (
              <ul className="ach-title-row">
                {acquiredTitles.map((t) => (
                  <li
                    key={t.code}
                    className={`ach-title-item ${t.active ? "is-active" : ""}`}
                  >
                    <div className="ach-title-main">
                      <p className="ach-title-name">
                        {t.name}
                        {t.hidden && (
                          <span className="home-badge home-badge--prog ach-hidden-badge">히든</span>
                        )}
                      </p>
                      <p className="ach-title-meta">
                        {t.description}
                        {t.acquiredAt
                          ? ` · ${formatAcquiredAt(t.acquiredAt)} 획득`
                          : ""}
                      </p>
                    </div>
                    <div className="ach-title-actions">
                      {t.active ? (
                        <span className="home-badge home-badge--done">장착 중</span>
                      ) : (
                        <button
                          type="button"
                          className="ach-btn-sm ach-btn-equip"
                          disabled={busyId !== null}
                          onClick={() => handleEquip(t.id)}
                        >
                          장착하기
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </>
  );
}

export default AchievementPanel;
