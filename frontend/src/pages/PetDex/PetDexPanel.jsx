import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  DEX_TIER_LABEL,
  DEX_TIERS,
  PET_CATALOG,
  STAGE_ORDER,
} from "../../components/petCatalog";
import { PetArt } from "../../components/petVisual";
import "../Home/HomeDashboard.css";
import "./PetDexPage.css";

/**
 * 종족별 사용자 상태를 계산한다.
 *  - raising : 지금 키우는 펫(released_at 이 비어 있는 펫)의 단계. 없으면 null.
 *  - doneCount : 다 키워서 배웅한 횟수(released_at 이 있는 펫 = 성체 배웅 완료).
 *  - reached : 지금까지 도달한 가장 높은 단계 인덱스(-1 = 아직 키워본 적 없음).
 */
export function buildStatusByKey(pets) {
  const byKey = {};
  for (const p of pets) {
    const key = p.appearanceKey;
    if (!byKey[key]) byKey[key] = { raising: null, doneCount: 0, reached: -1 };
    const s = byKey[key];
    if (p.releasedAt) {
      s.doneCount += 1;
      s.reached = Math.max(s.reached, STAGE_ORDER.indexOf("ADULT"));
    } else {
      s.raising = p.stage;
      s.reached = Math.max(s.reached, STAGE_ORDER.indexOf(p.stage));
    }
  }
  return byKey;
}

/** 도감의 "펫" 탭 — 종족별 카드와 내가 키운 기록. 데이터(pets·isAdmin)는 PetDexPage 가 불러 준다. */
function PetDexPanel({ pets, isAdmin, error }) {
  const [tier, setTier] = useState("ALL");
  // 도감 카드에서 보고 있는 단계(종족별 STAGE_ORDER 인덱스). 없으면 도달한 가장 높은 단계를 보여준다.
  const [viewingStage, setViewingStage] = useState({});

  const statusByKey = useMemo(() => buildStatusByKey(pets), [pets]);

  const visible =
    tier === "ALL" ? PET_CATALOG : PET_CATALOG.filter((sp) => sp.tier === tier);

  return (
    <>
      <div className="dex-filters" role="group" aria-label="등급 필터">
        {["ALL", ...DEX_TIERS].map((t) => (
          <button
            key={t}
            type="button"
            aria-pressed={tier === t}
            className={`dex-filter ${tier === t ? "is-active" : ""}`}
            onClick={() => setTier(t)}
          >
            {t === "ALL" ? "전체" : DEX_TIER_LABEL[t]}
          </button>
        ))}
      </div>

      {error && (
        <div className="dex-notice" role="alert">
          {error}
        </div>
      )}

      <ul className="dex-grid">
        {visible.map((sp) => {
          const st = statusByKey[sp.appearanceKey];
          const raising = st?.raising ?? null;
          const doneCount = st?.doneCount ?? 0;
          // 관리자: 전 단계 도달로 보고 잠금(흐림)을 걷는다. 배지는 실제 기록 그대로.
          const reached = isAdmin ? STAGE_ORDER.length - 1 : (st?.reached ?? -1);
          const owned = reached >= 0;
          // 도달한 단계 안에서만 골라 볼 수 있다. 고른 적 없으면 가장 높은 단계.
          const shownIndex = owned
            ? Math.min(viewingStage[sp.appearanceKey] ?? reached, reached)
            : -1;

          return (
            <li
              key={sp.appearanceKey}
              className={[
                "dex-card",
                raising ? "is-raising" : "",
                doneCount > 0 ? "is-done" : "",
                !owned ? "is-unowned" : "",
              ]
                .filter(Boolean)
                .join(" ")}
            >
              <div className="dex-card-badges">
                {raising && (
                  <span className="dex-badge dex-badge--raising">키우는 중</span>
                )}
                {doneCount > 0 && (
                  <span className="dex-badge dex-badge--done">
                    다 키움{doneCount > 1 ? ` ×${doneCount}` : ""}
                  </span>
                )}
                {isAdmin && !raising && doneCount === 0 && (
                  <span className="dex-badge dex-badge--admin">관리자 열람</span>
                )}
              </div>

              <div className="dex-art">
                <PetArt
                  appearanceKey={sp.appearanceKey}
                  stage={owned ? STAGE_ORDER[shownIndex] : undefined}
                  name={sp.name}
                  className="dex-art-img"
                  emojiClassName="dex-art-emoji"
                />
              </div>

              <div className="dex-card-top">
                <h2 className="dex-name">
                  <Link to={`/dex/${sp.appearanceKey}`} className="dex-card-link">
                    {sp.name}
                  </Link>
                </h2>
                <span className={`dex-tier dex-tier--${sp.tier}`}>
                  {DEX_TIER_LABEL[sp.tier]}
                </span>
              </div>

              <ol className="dex-stages" aria-label="성장 단계 — 도달한 단계를 누르면 그 모습을 볼 수 있어요">
                {STAGE_ORDER.map((stage, i) => {
                  const isReached = i <= reached;
                  const isViewing = i === shownIndex;
                  return (
                    <li
                      key={stage}
                      className={[
                        "dex-stage",
                        isReached ? "is-reached" : "",
                        raising === stage ? "is-current" : "",
                        isViewing ? "is-viewing" : "",
                      ]
                        .filter(Boolean)
                        .join(" ")}
                    >
                      <button
                        type="button"
                        className="dex-stage-btn"
                        disabled={!isReached}
                        aria-pressed={isViewing}
                        aria-label={`${sp.name} ${i + 1}차 모습 보기`}
                        onClick={() =>
                          setViewingStage((prev) => ({ ...prev, [sp.appearanceKey]: i }))
                        }
                      >
                        {i + 1}차
                      </button>
                    </li>
                  );
                })}
              </ol>

              <p className="dex-status">
                {raising
                  ? `지금 ${STAGE_ORDER.indexOf(raising) + 1}차 단계예요`
                  : doneCount > 0
                    ? "3차까지 키워 배웅했어요"
                    : isAdmin
                      ? "관리자 — 전 단계 열람 가능"
                      : "아직 키워보지 않았어요"}
              </p>
            </li>
          );
        })}
      </ul>
    </>
  );
}

export default PetDexPanel;
