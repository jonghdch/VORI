import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import AppRightSidebar from "../../components/AppRightSidebar";
import RecordCalendar, { dateKey } from "../../components/RecordCalendar";
import { getHomeSummary } from "../../api/home";
import { getMonthlyLedger } from "../../api/ledger";
import { getActivePet } from "../../api/pet";
import { getLatestDailyReport, markDailyReportRead } from "../../api/report";
import { PetArt } from "../../components/petVisual";
import "./HomeDashboard.css";

// 스탯 4종 표시 메타 (값은 백엔드 stats 에서).
const STAT_META = [
  { key: "energy", label: "에너지", color: "var(--home-bar-green)" },
  { key: "charm", label: "매력", color: "var(--home-bar-red)" },
  { key: "iq", label: "지능", color: "var(--home-bar-orange)" },
  { key: "endurance", label: "지구력", color: "var(--home-bar-blue)" },
];

// 업적은 아직 백엔드 도메인 미연동 — 정적 유지.
const ACHIEVEMENTS = [
  { title: "첫 지출 기록", status: "완료", done: true },
  { title: "한 주 예산 지키기", status: "진행중", done: false },
  { title: "카페 지출 줄이기", status: "진행중", done: false },
  { title: "업적 10개 달성", status: "완료", done: true },
];

const won = (n) => `${(n ?? 0).toLocaleString("ko-KR")}원`;

function HomeDashboard({ user, onNavigate, onLogout }) {
  const navigate = useNavigate();

  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    getHomeSummary()
      .then((res) => {
        if (alive) setSummary(res);
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  // 키우는 펫(이름·외형) + 최신 일일 리포트(펫 말풍선). 둘 다 실패해도 홈은 떠야 하므로 조용히 fallback.
  const [activePet, setActivePet] = useState(null);
  const [dailyReport, setDailyReport] = useState(null);
  useEffect(() => {
    let alive = true;
    getActivePet()
      .then((p) => alive && setActivePet(p))
      .catch(() => {});
    getLatestDailyReport()
      .then((r) => {
        if (!alive) return;
        setDailyReport(r);
        // 화면에 보인 순간 읽음 처리 — 실패해도 표시엔 영향 없음
        if (r && !r.readAt) markDailyReportRead(r.id).catch(() => {});
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  // 기록 캘린더 — 이번 달 가계부에서 기록이 있는 날짜(일) 집합. null = 로딩 중.
  const [recordedDays, setRecordedDays] = useState(null);

  useEffect(() => {
    let alive = true;
    const now = new Date();
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    getMonthlyLedger(ym)
      .then((rows) => {
        if (alive)
          setRecordedDays(
            new Set(
              rows.map((r) => {
                const [y, m, d] = r.date.split("-").map(Number);
                return dateKey(y, m, d);
              }),
            ),
          );
      })
      .catch(() => {
        if (alive) setRecordedDays(new Set());
      });
    return () => {
      alive = false;
    };
  }, []);

  const today = new Date();
  const dateStr = new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "long",
  }).format(today);

  const stats = summary?.stats;
  const spending = summary?.spending;
  const recent = summary?.recentExpenses ?? [];


  // 경험치바 — 프론트 임시 규칙: 스탯 4종 합 100당 1레벨, 나머지가 경험치.
  // 백엔드 exp 필드가 생기면 이 계산을 API 값으로 교체.
  const statTotal = STAT_META.reduce((s, m) => s + (stats?.[m.key] ?? 0), 0);
  const petLevel = Math.floor(statTotal / 100) + 1;
  const petExp = statTotal % 100;

  return (
    <AppShell
      activeTop="home"
      activeSide="home"
      onNavigate={onNavigate}
      onLogout={onLogout}
    >
      <main className="home-main">
        <div className="home-row home-row-pet">
          {/* 성장 단계·상태·AI 멘트는 데이터 소스가 없어 정적 문구였음 — 허위 노출 대신
              실지출 기반 말풍선만 유지. 펫 상태 API 가 생기면 단계/상태 표시 복원. */}
          <section className="home-card home-card-pet">
            <div className="home-pet-top">
              <p className="home-date">{dateStr}</p>
              <button
                type="button"
                className="home-pet-room-link"
                onClick={() => navigate("/raise")}
              >
                마이룸 가기 →
              </button>
            </div>
            {/* 말풍선 — 일일 리포트의 AI 코멘트가 있으면 그걸, 없으면 오늘 지출 기반 문구 */}
            <div className="home-pet-bubble">
              {dailyReport?.aiComment ? (
                <>
                  <span className="home-pet-bubble-date">
                    {dailyReport.reportDate.slice(5).replace("-", "/")} 리포트
                  </span>
                  {dailyReport.aiComment}
                </>
              ) : loading ? (
                "오늘 소비를 살펴보고 있어요…"
              ) : (spending?.today ?? 0) > 0 ? (
                `오늘 ${won(spending.today)} 지출했어요. 저녁 8시에 같이 돌아봐요!`
              ) : (
                "오늘은 아직 지출 기록이 없어요. 첫 기록을 남겨볼까요?"
              )}
            </div>
            <div className="home-pet-body">
              <div className="home-pet-center">
                {/* 원형 경험치 게이지 — 270° 아치(아래 90° 열림)가 보리를 감싼다.
                    프론트 임시 규칙: 스탯 4종 합 100당 1레벨, 나머지가 경험치.
                    백엔드 exp 필드가 생기면 이 계산을 API 값으로 교체. */}
                <div
                  className="home-pet-gauge"
                  role="img"
                  aria-label={`경험치 ${petExp}/100 (Lv. ${petLevel})`}
                >
                  <svg className="home-pet-gauge-ring" viewBox="0 0 120 120" aria-hidden>
                    <circle
                      className="home-pet-gauge-track"
                      cx="60" cy="60" r="52"
                      transform="rotate(135 60 60)"
                      strokeDasharray="245.04 326.73"
                    />
                    <circle
                      className="home-pet-gauge-fill"
                      cx="60" cy="60" r="52"
                      transform="rotate(135 60 60)"
                      strokeDasharray={`${(245.04 * petExp) / 100} 326.73`}
                    />
                  </svg>
                  <div className="home-pet-art" aria-hidden>
                    <PetArt
                      appearanceKey={activePet?.appearanceKey ?? "puppy"}
                      name=""
                      className="home-pet-image"
                      emojiClassName="home-pet-emoji"
                    />
                  </div>
                  {/* 칭호 — 게이지 하단 열린 틈에 배치. API 미구현, "칭호 없음" */}
                  <span className="home-pet-title-badge">칭호 없음</span>
                </div>
                <div className="home-pet-name-line">
                  <h2 className="home-pet-name">{activePet?.speciesName ?? "보리"}</h2>
                  <span className="home-pet-level-label">Lv. {petLevel}</span>
                </div>
              </div>
              <ul className="home-stat-list home-pet-stats">
                {STAT_META.map((m) => {
                  const value = stats?.[m.key] ?? 0;
                  return (
                    <li key={m.key} className="home-stat-row">
                      <span className="home-stat-label">{m.label}</span>
                      <div className="home-stat-track">
                        <div
                          className="home-stat-fill"
                          style={{
                            width: `${Math.min(Math.max(value, 0), 100)}%`,
                            background: m.color,
                          }}
                        />
                      </div>
                    </li>
                  );
                })}
              </ul>
            </div>
          </section>
        </div>

        <div className="home-row home-row-kpi">
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">오늘 지출</h3>
            <p className="home-kpi-value">{won(spending?.today)}</p>
            {/* recent.length 는 "최근 지출 5건" 목록 길이지 오늘 기록 수가 아님 — 오표기 제거 */}
            <p className="home-kpi-sub">오늘 0시부터 누적</p>
          </article>
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">이번 달 누적</h3>
            <p className="home-kpi-value">{won(spending?.thisMonth)}</p>
            <p className="home-kpi-sub">이번 달 총 지출</p>
          </article>
          <article className="home-card home-kpi">
            <h3 className="home-kpi-title">이번 주 지출</h3>
            <p className="home-kpi-value">{won(spending?.thisWeek)}</p>
            <p className="home-kpi-sub">월요일부터 누적</p>
          </article>
        </div>

        <div className="home-row home-row-bottom">
          <section className="home-card home-card-list">
            <div className="home-list-head">
              <h2 className="home-card-title home-card-title--sm">최근 지출 내역</h2>
              <p className="home-list-date">최근 {recent.length}건</p>
            </div>
            <ul className="home-tx-list">
              {loading ? (
                <li className="home-tx-row">불러오는 중…</li>
              ) : recent.length === 0 ? (
                <li className="home-tx-row">아직 지출 기록이 없어요.</li>
              ) : (
                recent.map((row) => (
                  <li key={row.id} className="home-tx-row">
                    <span className="home-tx-icon">
                      <span className={`home-tx-dot home-tx-dot--${(row.signalFinal || "GRAY").toLowerCase()}`} />
                    </span>
                    <div className="home-tx-mid">
                      <span className="home-tx-name">{row.item}</span>
                      <span className="home-tx-cat">{row.categoryName}</span>
                    </div>
                    <span className="home-tx-amount">{won(row.amount)}</span>
                  </li>
                ))
              )}
            </ul>
            <div className="home-card-actions">
              <button
                type="button"
                className="home-link-btn"
                onClick={() => navigate("/wallet")}
              >
                ▶ 상세 내역 확인하기
              </button>
              <button
                type="button"
                className="home-btn home-btn-dark"
                onClick={() => navigate("/wallet/new")}
              >
                + 지출 추가하기
              </button>
            </div>
          </section>

          <section className="home-card home-card-achieve">
            <h2 className="home-card-title home-card-title--sm">최근 업적</h2>
            <ul className="home-ach-list">
              {ACHIEVEMENTS.map((a) => (
                <li key={a.title} className="home-ach-row">
                  <span className="home-ach-title">{a.title}</span>
                  <span
                    className={`home-badge ${a.done ? "home-badge--done" : "home-badge--prog"}`}
                  >
                    {a.status}
                  </span>
                </li>
              ))}
            </ul>
            <button type="button" className="home-btn home-btn-primary home-btn-block">
              ▶ 더 많은 업적 확인하기
            </button>
          </section>

          <section className="home-card home-card-chart">
            <h2 className="home-card-title home-card-title--sm">
              {today.getMonth() + 1}월 기록 캘린더
            </h2>
            <div className="home-cal">
              <RecordCalendar
                year={today.getFullYear()}
                month={today.getMonth() + 1}
                recordedKeys={recordedDays}
              />
            </div>
            <button
              type="button"
              className="home-btn home-btn-primary home-btn-block"
              onClick={() => navigate("/wallet")}
            >
              ▶ 리포트 확인하기
            </button>
          </section>
        </div>

        <p className="home-footnote">매일 오후 8시에 보리가 소비 검사를 시작해요</p>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

export default HomeDashboard;
