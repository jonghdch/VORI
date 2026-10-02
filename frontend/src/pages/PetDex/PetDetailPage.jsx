import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import CoinIcon from "../../components/CoinIcon";
import { listPets } from "../../api/pet";
import { PET_CHANGED_EVENT, getMe } from "../../api/user";
import { DEX_TIER_LABEL, PET_CATALOG, STAGE_ORDER } from "../../components/petCatalog";
import { PetArt, STAGE_LABEL, VARIANT_LABEL, petDisplayName } from "../../components/petVisual";
import { buildStatusByKey } from "./PetDexPanel";
import "../Home/HomeDashboard.css";
import "./PetDexPage.css";

const formatDate = (iso) => (iso ? iso.slice(0, 10).replaceAll("-", ". ") : "");

/**
 * 펫 상세 — 도감 카드를 누르면 온다 (/dex/:appearanceKey).
 * 종족 한 마리의 단계별 모습과, 내가 이 종족을 키운 기록(키우는 중·분양한 펫)을 보여 준다.
 */
function PetDetailPage({ onLogout }) {
  const { appearanceKey } = useParams();
  const navigate = useNavigate();
  const species = PET_CATALOG.find((sp) => sp.appearanceKey === appearanceKey);

  const [pets, setPets] = useState([]);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [viewing, setViewing] = useState(null); // STAGE_ORDER 인덱스. null = 도달한 가장 높은 단계

  useEffect(() => {
    let alive = true;
    getMe()
      .then((me) => alive && setIsAdmin(me?.role === "ADMIN"))
      .catch(() => {});
    const load = () =>
      listPets().then((data) => {
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
        setError(e.message || "펫 기록을 불러오지 못했어요");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
      window.removeEventListener(PET_CHANGED_EVENT, onPetChanged);
    };
  }, [navigate]);

  // 이 종족으로 키운 펫들 — 키우는 중이 맨 앞, 나머지는 최신순(listPets 가 최신순으로 준다)
  const mine = useMemo(
    () =>
      pets
        .filter((p) => p.appearanceKey === appearanceKey)
        .sort((a, b) => (a.releasedAt ? 1 : 0) - (b.releasedAt ? 1 : 0)),
    [pets, appearanceKey],
  );
  const status = useMemo(() => buildStatusByKey(pets)[appearanceKey], [pets, appearanceKey]);

  if (!species) return <Navigate to="/dex" replace />;

  const raising = status?.raising ?? null;
  const doneCount = status?.doneCount ?? 0;
  // 관리자는 전 단계를 볼 수 있다 (도감과 같은 기조)
  const reached = isAdmin ? STAGE_ORDER.length - 1 : (status?.reached ?? -1);
  const owned = reached >= 0;
  const shownIndex = owned ? Math.min(viewing ?? reached, reached) : -1;

  return (
    <AppShell activeTop="raise" activeSide="dex" onLogout={onLogout}>
      <main className="home-main dex-main">
        <Link to="/dex" className="dexd-back">
          ← 도감
        </Link>

        <section className="home-card dexd-hero">
          <div className="dexd-art">
            <PetArt
              appearanceKey={species.appearanceKey}
              stage={owned ? STAGE_ORDER[shownIndex] : undefined}
              name={species.name}
              className="dexd-art-img"
              emojiClassName="dexd-art-emoji"
            />
          </div>
          <div className="dexd-info">
            <div className="dex-card-badges dexd-badges">
              {raising && <span className="dex-badge dex-badge--raising">키우는 중</span>}
              {doneCount > 0 && (
                <span className="dex-badge dex-badge--done">다 키움{doneCount > 1 ? ` ×${doneCount}` : ""}</span>
              )}
              {isAdmin && !raising && doneCount === 0 && (
                <span className="dex-badge dex-badge--admin">관리자 열람</span>
              )}
            </div>
            <h1 className="dexd-name">
              {species.name}
              <span className={`dex-tier dex-tier--${species.tier}`}>{DEX_TIER_LABEL[species.tier]}</span>
            </h1>
            <p className="dexd-lead">
              {raising
                ? `지금 ${STAGE_ORDER.indexOf(raising) + 1}차 단계로 키우고 있어요.`
                : doneCount > 0
                  ? "3차까지 키워 분양한 친구예요."
                  : isAdmin
                    ? "관리자 — 전 단계를 볼 수 있어요."
                    : "아직 키워보지 않은 친구예요. 상점에서 알을 데려오면 만날 수 있어요."}
            </p>

            <ol className="dexd-stages" aria-label="성장 단계 — 도달한 단계를 누르면 그 모습을 볼 수 있어요">
              {STAGE_ORDER.map((stage, i) => {
                const isReached = i <= reached;
                const isViewing = i === shownIndex;
                return (
                  <li key={stage}>
                    <button
                      type="button"
                      className={`dexd-stage-btn ${isReached ? "is-reached" : ""} ${isViewing ? "is-viewing" : ""} ${raising === stage ? "is-current" : ""}`}
                      disabled={!isReached}
                      aria-pressed={isViewing}
                      onClick={() => setViewing(i)}
                    >
                      {STAGE_LABEL[stage]}
                      {raising === stage && <small>키우는 중</small>}
                      {!isReached && <small>미도달</small>}
                    </button>
                  </li>
                );
              })}
            </ol>
          </div>
        </section>

        <section className="home-card dexd-records">
          <div className="dexd-records-head">
            <h2 className="home-card-title home-card-title--sm">내 기록</h2>
            <span>{loading ? "…" : `${mine.length}마리`}</span>
          </div>
          {error && (
            <div className="dex-notice" role="alert">
              {error}
            </div>
          )}
          {!loading && !error && mine.length === 0 && (
            <div className="dexd-empty">
              <p>아직 이 친구를 키워본 적이 없어요.</p>
              <button type="button" className="home-btn home-btn-primary" onClick={() => navigate("/shop")}>
                상점 가기
              </button>
            </div>
          )}
          {mine.length > 0 && (
            <ul className="dexd-record-list">
              {mine.map((p) => (
                <li key={p.id} className={`dexd-record ${p.releasedAt ? "is-released" : "is-raising"}`}>
                  <span className="dexd-record-art">
                    <PetArt
                      appearanceKey={p.appearanceKey}
                      stage={p.stage}
                      name=""
                      className="dexd-record-img"
                      emojiClassName="dexd-record-emoji"
                    />
                  </span>
                  <div className="dexd-record-info">
                    <strong>
                      {petDisplayName(p)}
                      {VARIANT_LABEL[p.variant] && <span className="dex-badge dex-badge--done">{VARIANT_LABEL[p.variant]}</span>}
                    </strong>
                    <small>
                      {STAGE_LABEL[p.stage]} · Lv. {p.level} · {formatDate(p.hatchedAt)} 부화
                    </small>
                    {/* 이 펫이 얻은 칭호 — 분양한 펫이면 분양 순간의 기록. ★ = 장착한 칭호 */}
                    {p.titles?.length > 0 && (
                      <ul className="dexd-record-titles" aria-label={`${petDisplayName(p)}이(가) 얻은 칭호`}>
                        {p.titles.map((t) => {
                          const equipped = p.equippedTitle?.awardId === t.awardId;
                          return (
                            <li key={t.awardId} className={`dexd-record-title ${equipped ? "is-equipped" : ""}`}>
                              {equipped && <span aria-label="장착한 칭호">★ </span>}
                              {t.name}
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>
                  <span className="dexd-record-state">
                    {p.releasedAt ? (
                      <>
                        {formatDate(p.releasedAt)} 분양 · <CoinIcon /> {(p.releaseValue ?? 0).toLocaleString("ko-KR")}
                      </>
                    ) : (
                      <Link to="/raise" className="home-link-btn">
                        마이룸에서 보기 →
                      </Link>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>
      <AppRightSidebar />
    </AppShell>
  );
}

export default PetDetailPage;
