import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import { listPets } from "../../api/pet";
import { PET_CHANGED_EVENT, getMe } from "../../api/user";
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
 *  - doneCount : 다 키워서 분양한 횟수(released_at 이 있는 펫 = 성체 분양 완료).
 *  - reached : 지금까지 도달한 가장 높은 단계 인덱스(-1 = 아직 키워본 적 없음).
 */
function buildStatusByKey(pets) {
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

function PetDexPage({ onLogout }) {
  const navigate = useNavigate();
  const [pets, setPets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [tier, setTier] = useState("ALL");
  // 관리자는 도감이 전부 열린 상태로 본다 — 관리자 혜택(코인 무제한·칭호 전부)과 같은 기조.
  // 실제로 키운 기록(키우는 중·다 키움 배지)은 그대로 보여 주고, 잠금 표시만 걷는다.
  const [isAdmin, setIsAdmin] = useState(false);
  useEffect(() => {
    let alive = true;
    getMe()
      .then((me) => alive && setIsAdmin(me?.role === "ADMIN"))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 도감 카드에서 보고 있는 단계(종족별 STAGE_ORDER 인덱스). 없으면 도달한 가장 높은 단계를 보여준다.
  const [viewingStage, setViewingStage] = useState({});

  useEffect(() => {
    let alive = true;
    const load = () => listPets()
      .then((data) => {
        if (alive) setPets(Array.isArray(data) ? data : []);
      });
    const onPetChanged = () => load().catch(() => {});
    window.addEventListener(PET_CHANGED_EVENT, onPetChanged);
    load()
      .catch((e) => {
        if (!alive) return;
        if (e.status === 401) {
          navigate("/login", { replace: true });
          return;
        }
        setError(e.message || "펫 도감을 불러오지 못했어요");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
      window.removeEventListener(PET_CHANGED_EVENT, onPetChanged);
    };
  }, [navigate]);

  const statusByKey = useMemo(() => buildStatusByKey(pets), [pets]);

  const raisingSpecies = useMemo(
    () => PET_CATALOG.find((sp) => statusByKey[sp.appearanceKey]?.raising),
    [statusByKey],
  );
  const doneKinds = useMemo(
    () =>
      PET_CATALOG.filter((sp) => statusByKey[sp.appearanceKey]?.doneCount > 0)
        .length,
    [statusByKey],
  );

  const visible =
    tier === "ALL" ? PET_CATALOG : PET_CATALOG.filter((sp) => sp.tier === tier);

  return (
    <AppShell activeTop="raise" activeSide="dex" onLogout={onLogout}>
      <main className="home-main dex-main">
        <header className="dex-head">
          <h1 className="dex-title">
            <span className="dex-title-badge" aria-hidden>
              📖
            </span>
            펫 도감
          </h1>
          <p>
            키울 수 있는 펫 {PET_CATALOG.length}종을 모두 모았어요. 지금 키우는 펫과
            끝까지 키워 분양한 펫이 표시돼요.
          </p>
        </header>

        <div className="dex-summary">
          <article className="dex-summary-card">
            <p className="dex-summary-label">전체 펫 종류</p>
            <p className="dex-summary-value">{PET_CATALOG.length}종</p>
          </article>
          <article className="dex-summary-card">
            <p className="dex-summary-label">키우는 중</p>
            <p className="dex-summary-value">
              {loading ? "…" : raisingSpecies ? raisingSpecies.name : "없음"}
            </p>
          </article>
          <article className="dex-summary-card">
            <p className="dex-summary-label">다 키운 펫</p>
            <p className="dex-summary-value">
              {loading
                ? "…"
                : isAdmin
                  ? `${PET_CATALOG.length} / ${PET_CATALOG.length}종 (관리자)`
                  : `${doneKinds} / ${PET_CATALOG.length}종`}
            </p>
          </article>
        </div>

        <div className="dex-filters" role="tablist" aria-label="등급 필터">
          {["ALL", ...DEX_TIERS].map((t) => (
            <button
              key={t}
              type="button"
              role="tab"
              aria-selected={tier === t}
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
                  <h2 className="dex-name">{sp.name}</h2>
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
                      ? "3차까지 키워 분양했어요"
                      : isAdmin
                        ? "관리자 — 전 단계 열람 가능"
                        : "아직 키워보지 않았어요"}
                </p>
              </li>
            );
          })}
        </ul>
      </main>
      <AppRightSidebar />
    </AppShell>
  );
}

export default PetDexPage;
