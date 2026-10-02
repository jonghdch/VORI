import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getPetTitleBoard, equipPetTitle } from "../../api/petTitles";
import { PET_CHANGED_EVENT } from "../../api/user";
import { petDisplayName } from "../../components/petVisual";
import "../Achievement/AchievementPage.css";

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

const UNIT = {
  LEVEL: (c, t) => `Lv. ${c} / Lv. ${t}`,
  INTERACTIONS: (c, t) => `${c}회 / ${t}회 상호작용`,
  AI_ANSWERS: (c, t) => `${c}회 / ${t}회 AI 답변`,
  CHARM_BONUS: (c, t) => `${c}회 / ${t}회 매력 보너스`,
};

// 못 딴 히든 칭호는 서버가 조건을 가려서 보낸다(설명 "???") — 달성률만
const progressText = (item) => (item.hidden
  ? `${item.progressPct}%`
  : (UNIT[item.metricType] ?? ((c, t) => `${c} / ${t}`))(item.current, item.threshold));

/**
 * 도감의 "칭호" 탭 — 지금 키우는 펫의 칭호 과제. 칭호는 펫이 얻고, 펫이 바뀌면 과제는 0부터 다시 시작한다.
 * 딴 칭호 중 하나를 장착하면 홈 배지에 보인다. 졸업한 펫의 칭호는 펫 탭의 상세 기록에서 본다.
 */
function PetTitlePanel() {
  const navigate = useNavigate();
  const [board, setBoard] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => getPetTitleBoard()
    .then((data) => {
      setBoard(data);
      setError(null);
    })
    .catch((e) => setError(e.message || "칭호를 불러오지 못했어요")), []);

  useEffect(() => {
    load();
    window.addEventListener(PET_CHANGED_EVENT, load);
    return () => window.removeEventListener(PET_CHANGED_EVENT, load);
  }, [load]);

  // 안내 문구는 10초 뒤에 저절로 사라진다
  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(null), 10000);
    return () => clearTimeout(timer);
  }, [notice]);

  const feature = async (awardId) => {
    setBusy(true);
    setNotice(null);
    try {
      setBoard(await equipPetTitle(board.petId, awardId));
      setNotice({ kind: "ok", text: awardId ? "칭호를 장착했어요. 홈에서 보여요." : "칭호 장착을 해제했어요." });
      // 홈 배지·내 정보 상자·도감 요약이 장착 칭호를 다시 읽게 한다
      window.dispatchEvent(new Event(PET_CHANGED_EVENT));
      window.dispatchEvent(new Event("vori:account-updated"));
    } catch (e) {
      setNotice({ kind: "err", text: e.message || "칭호를 장착하지 못했어요" });
    } finally {
      setBusy(false);
    }
  };

  if (error) return <div className="ach-notice ach-notice--err" role="alert">{error}</div>;
  if (!board) return <p className="ach-empty">불러오는 중…</p>;

  const hasPet = board.petId != null;
  const equipped = board.titles.find((t) => t.equipped) ?? null;
  const petName = hasPet ? petDisplayName({ name: board.petName, speciesName: board.speciesName }) : null;

  return (
    <>
      {notice && (
        <div className={`ach-notice ach-notice--${notice.kind}`} role="status">
          {notice.text}
        </div>
      )}

      {hasPet ? (
        <div className="ach-equipped">
          <div>
            <p className="ach-equipped-label">장착중인 칭호</p>
            <p className="ach-equipped-name">{equipped ? equipped.name : "장착한 칭호 없음"}</p>
          </div>
        </div>
      ) : (
        <div className="ach-equipped">
          <div>
            <p className="ach-equipped-label">지금 키우는 펫이 없어요</p>
            <p className="ach-equipped-name">알을 열면 새 칭호 과제가 시작돼요</p>
          </div>
          <div className="ach-title-actions">
            <button type="button" className="ach-btn-sm ach-btn-equip" onClick={() => navigate("/shop")}>
              상점 가기
            </button>
          </div>
        </div>
      )}

      <section className="ach-section">
        <h2 className="ach-section-title">{hasPet ? `${petName}의 칭호 과제` : "칭호 과제"}</h2>
        <ul className="ach-title-row">
          {board.titles.map((t) => (
            <li key={t.code} className={`ach-title-item ${t.equipped ? "is-active" : ""} ${t.acquired ? "" : "is-locked"}`}>
              <div className="ach-title-main">
                <p className="ach-title-name">
                  {t.name}
                  {t.hidden && <span className="home-badge home-badge--prog ach-hidden-badge">히든</span>}
                </p>
                <p className="ach-title-meta">{t.description}</p>
                {!t.acquired && (
                  <>
                    <div className="ach-progress-row">
                      <span>달성도</span>
                      <span>{progressText(t)}</span>
                    </div>
                    <div className="ach-progress-track" aria-hidden>
                      <div className="ach-progress-fill" style={{ width: `${t.progressPct}%` }} />
                    </div>
                  </>
                )}
              </div>
              {t.acquired && (
                <div className="ach-title-actions">
                  <span className="ach-title-date">{formatAcquiredAt(t.acquiredAt)} 획득</span>
                  {t.equipped ? (
                    <button type="button" className="ach-btn-sm ach-btn-ghost" disabled={busy} onClick={() => feature(null)}>
                      장착 해제
                    </button>
                  ) : (
                    <button type="button" className="ach-btn-sm ach-btn-equip" disabled={busy} onClick={() => feature(t.awardId)}>
                      장착하기
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
        <p className="ach-empty">졸업한 펫이 얻은 칭호는 펫 탭에서 그 펫을 눌러 볼 수 있어요.</p>
      </section>
    </>
  );
}

export default PetTitlePanel;
