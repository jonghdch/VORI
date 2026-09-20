import { useEffect, useMemo, useState } from "react";
import AppRightSidebar from "../../components/AppRightSidebar";
import AppShell from "../../components/AppShell";
import { listTitles, setActiveTitle } from "../../api/titles";
import "../Home/HomeDashboard.css";
import "./TitlesPage.css";

function formatNumber(n) {
  return Number(n || 0).toLocaleString("ko-KR");
}

function TitlesPage({ user, onLogout }) {
  const [titles, setTitles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [savingId, setSavingId] = useState(null);

  const load = () => {
    setLoading(true);
    setError("");
    listTitles()
      .then((data) => setTitles(Array.isArray(data) ? data : []))
      .catch((e) => setError(e.message || "칭호를 불러오지 못했어요"))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const activeTitle = titles.find((t) => t.active);
  const acquired = useMemo(() => titles.filter((t) => t.acquired), [titles]);
  const locked = useMemo(() => titles.filter((t) => !t.acquired), [titles]);

  const activate = async (title) => {
    if (!title.acquired || savingId !== null) return;
    const nextId = title.active ? null : title.id;
    setSavingId(title.id);
    setError("");
    try {
      await setActiveTitle(nextId);
      setTitles((prev) =>
        prev.map((t) => ({
          ...t,
          active: nextId != null && t.id === nextId,
        })),
      );
    } catch (e) {
      setError(e.message || "칭호를 변경하지 못했어요");
    } finally {
      setSavingId(null);
    }
  };

  return (
    <AppShell
      activeTop="home"
      activeSide="achievement"
      user={user}
      onLogout={onLogout}
    >
      <main className="home-main titles-main">
        <div className="titles-header">
          <div>
            <h1 className="titles-title">업적/칭호</h1>
            <p className="titles-subtitle">
              장착 칭호: {activeTitle?.name || "없음"}
            </p>
          </div>
          <div className="titles-counts" aria-label="칭호 현황">
            <span>{acquired.length} 획득</span>
            <span>{locked.length} 진행 중</span>
          </div>
        </div>

        {error && (
          <div className="titles-alert">
            <span>{error}</span>
            <button type="button" onClick={load}>다시 시도</button>
          </div>
        )}

        {loading ? (
          <section className="home-card titles-state">불러오는 중...</section>
        ) : (
          <div className="titles-grid">
            {titles.map((title) => (
              <article
                key={title.code}
                className={`home-card title-card ${title.acquired ? "is-acquired" : "is-locked"} ${title.active ? "is-active" : ""}`}
              >
                <div className="title-card-head">
                  <div>
                    <h2>{title.name}</h2>
                    <p>{title.description}</p>
                  </div>
                  {title.active && <span className="title-active-badge">장착 중</span>}
                </div>

                <div className="title-progress-row">
                  <span>{formatNumber(title.current)} / {formatNumber(title.threshold)}</span>
                  <span>{title.progressPct}%</span>
                </div>
                <div className="title-progress-track" aria-hidden>
                  <div style={{ width: `${Math.min(Math.max(title.progressPct, 0), 100)}%` }} />
                </div>

                <button
                  type="button"
                  className="title-action"
                  disabled={!title.acquired || savingId !== null}
                  onClick={() => activate(title)}
                >
                  {!title.acquired
                    ? "미획득"
                    : title.active
                      ? "장착 해제"
                      : savingId === title.id
                        ? "저장 중..."
                        : "장착하기"}
                </button>
              </article>
            ))}
          </div>
        )}
      </main>
      <AppRightSidebar />
    </AppShell>
  );
}

export default TitlesPage;
