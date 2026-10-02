import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import { listPets } from "../../api/pet";
import { listTitles } from "../../api/titles";
import { PET_CHANGED_EVENT, getMe } from "../../api/user";
import { PET_CATALOG } from "../../components/petCatalog";
import PetDexPanel, { buildStatusByKey } from "./PetDexPanel";
import AchievementPanel from "../Achievement/AchievementPanel";
import "../Home/HomeDashboard.css";
import "./PetDexPage.css";

// 도감 = 펫 · 업적 · 칭호 한 화면. 위에 요약 세 칸, 그 아래 탭.
// 탭은 ?tab= 으로 남겨 링크로 바로 열 수 있다 (/dex?tab=titles).
const TABS = [
  { id: "pets", label: "펫" },
  { id: "achievements", label: "업적" },
  { id: "titles", label: "칭호" },
];

function PetDexPage({ onLogout }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const current = TABS.find((t) => t.id === searchParams.get("tab")) ?? TABS[0];
  const selectTab = (id) => setSearchParams(id === TABS[0].id ? {} : { tab: id }, { replace: true });

  // 세 탭이 같은 데이터를 보므로 여기서 한 번만 읽는다.
  const [pets, setPets] = useState([]);
  const [titles, setTitles] = useState([]);
  // 관리자는 도감이 전부 열린 상태로 본다 — 관리자 혜택(코인 무제한·칭호 전부)과 같은 기조.
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  // 펫·칭호는 따로 실패할 수 있어 에러도 따로 둔다 — 칭호만 실패해도 펫 탭에 에러가 뜨지 않게.
  const [petError, setPetError] = useState(null);
  const [titleError, setTitleError] = useState(null);

  const loadPets = useCallback(() => listPets().then((data) => {
    setPets(Array.isArray(data) ? data : []);
    setPetError(null);
  }), []);
  const loadTitles = useCallback(() => listTitles().then((data) => {
    setTitles(Array.isArray(data) ? data : []);
    setTitleError(null);
  }), []);

  useEffect(() => {
    let alive = true;
    getMe()
      .then((me) => alive && setIsAdmin(me?.role === "ADMIN"))
      .catch(() => {});
    // 둘 다 끝날 때까지 기다린다 — 하나가 먼저 실패해도 다른 쪽이 불러오는 중이면 로딩을 유지한다.
    Promise.allSettled([loadPets(), loadTitles()]).then(([pets, titles]) => {
      if (!alive) return;
      const failed = [pets, titles].filter((r) => r.status === "rejected").map((r) => r.reason);
      if (failed.some((e) => e?.status === 401)) {
        navigate("/login", { replace: true });
        return;
      }
      if (pets.status === "rejected") setPetError(pets.reason?.message || "도감을 불러오지 못했어요");
      if (titles.status === "rejected") setTitleError(titles.reason?.message || "칭호를 불러오지 못했어요");
      setLoading(false);
    });
    // 관리자 도구·분양 등으로 펫이 바뀌면 다시 읽는다
    const onPetChanged = () => loadPets().catch(() => {});
    window.addEventListener(PET_CHANGED_EVENT, onPetChanged);
    return () => {
      alive = false;
      window.removeEventListener(PET_CHANGED_EVENT, onPetChanged);
    };
  }, [loadPets, loadTitles, navigate]);

  const acquiredCount = useMemo(() => titles.filter((t) => t.acquired).length, [titles]);
  const doneKinds = useMemo(() => {
    const statusByKey = buildStatusByKey(pets);
    return PET_CATALOG.filter((sp) => statusByKey[sp.appearanceKey]?.doneCount > 0).length;
  }, [pets]);

  return (
    <AppShell activeTop="raise" activeSide="dex" onLogout={onLogout}>
      <main className="home-main dex-main">
        <h1 className="dex-sr-title">도감</h1>

        <div className="dex-summary">
          <article className="dex-summary-card">
            <p className="dex-summary-label">달성 업적</p>
            <p className="dex-summary-value">{loading ? "…" : `${acquiredCount} / ${titles.length}`}</p>
          </article>
          <article className="dex-summary-card">
            <p className="dex-summary-label">획득 칭호</p>
            <p className="dex-summary-value">{loading ? "…" : `${acquiredCount}개`}</p>
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

        <div className="dex-tabs" role="tablist" aria-label="도감 구분">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              role="tab"
              id={`dex-tab-${t.id}`}
              aria-selected={current.id === t.id}
              aria-controls="dex-panel"
              className={`dex-tab ${current.id === t.id ? "is-active" : ""}`}
              onClick={() => selectTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div id="dex-panel" role="tabpanel" aria-labelledby={`dex-tab-${current.id}`}>
          {current.id === "pets" ? (
            <PetDexPanel pets={pets} isAdmin={isAdmin} error={petError} />
          ) : (
            <AchievementPanel
              tab={current.id}
              titles={titles}
              loading={loading}
              error={titleError}
              reload={loadTitles}
            />
          )}
        </div>
      </main>
      <AppRightSidebar />
    </AppShell>
  );
}

export default PetDexPage;
