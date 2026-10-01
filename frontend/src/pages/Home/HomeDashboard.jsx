import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppShell from "../../components/AppShell";
import AppRightSidebar from "../../components/AppRightSidebar";
import RecordCalendar, { dateKey } from "../../components/RecordCalendar";
import { getHomeSummary } from "../../api/home";
import { getMonthlyLedger } from "../../api/ledger";
import { getActivePet } from "../../api/pet";
import { PET_CHANGED_EVENT } from "../../api/user";
import { getLatestDailyReport, markDailyReportRead } from "../../api/report";
import { listTitles } from "../../api/titles";
import { PetArt, petDisplayName } from "../../components/petVisual";
import { AI_ACTIVE_FROM_HOUR } from "../../config";
import "./HomeDashboard.css";

// 스탯 4종 표시 메타 — 값은 키우는 펫 본인의 스탯(PetResponse.stat*). 펫 화면과 같은 값이다.
// 홈 요약(summary.stats)은 지출에 쌓인 변동만 합산해서, 출석 아이템처럼 펫에만 반영되는
// 변화가 빠진다. 그래서 이 카드에는 쓰지 않는다.
const STAT_META = [
  { key: "statEnergy", label: "에너지", color: "var(--home-bar-green)" },
  { key: "statCharm", label: "매력", color: "var(--home-bar-red)" },
  { key: "statIq", label: "지능", color: "var(--home-bar-orange)" },
  { key: "statEndurance", label: "지구력", color: "var(--home-bar-blue)" },
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

  // 키우는 펫(이름·외형·스탯) + 최신 일일 리포트(펫 말풍선). 둘 다 실패해도 홈은 떠야 하므로 조용히 fallback.
  const [activePet, setActivePet] = useState(null);
  // 조회가 끝나기 전에는 "펫 없음" 안내를 띄우지 않는다 — 잠깐 떴다 사라지는 깜빡임 방지
  const [petLoaded, setPetLoaded] = useState(false);
  const [dailyReport, setDailyReport] = useState(null);
  const [titles, setTitles] = useState([]);
  const [titlesLoading, setTitlesLoading] = useState(true);
  useEffect(() => {
    let alive = true;
    const loadPet = () =>
      getActivePet()
        .then((p) => {
          if (!alive) return;
          setActivePet(p);
          // 조회에 성공했을 때만 "불러옴" — 실패를 펫 없음으로 보고 상점 안내를 띄우지 않게
          setPetLoaded(true);
        })
        .catch(() => {});
    loadPet();
    window.addEventListener(PET_CHANGED_EVENT, loadPet);
    getLatestDailyReport()
      .then((r) => {
        if (!alive) return;
        setDailyReport(r);
        // 화면에 보인 순간 읽음 처리 — 실패해도 표시엔 영향 없음
        if (r && !r.readAt) markDailyReportRead(r.id).catch(() => {});
      })
      .catch(() => {});
    listTitles()
      .then((data) => {
        if (alive) setTitles(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (alive) setTitles([]);
      })
      .finally(() => {
        if (alive) setTitlesLoading(false);
      });
    return () => {
      alive = false;
      window.removeEventListener(PET_CHANGED_EVENT, loadPet);
    };
  }, []);

  // 기록 캘린더 — 이번 달 가계부에서 기록이 있는 날짜(일) 집합. null = 로딩 중.
  const [recordedDays, setRecordedDays] = useState(null);
  const [calendarSignals, setCalendarSignals] = useState(new Map());

  useEffect(() => {
    let alive = true;
    const now = new Date();
    const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    getMonthlyLedger(ym)
      .then((rows) => {
        if (alive) {
          setRecordedDays(
            new Set(
              rows.map((r) => {
                const [y, m, d] = r.date.split("-").map(Number);
                return dateKey(y, m, d);
              }),
            ),
          );
          setCalendarSignals(buildSignalMap(rows));
        }
      })
      .catch(() => {
        if (alive) {
          setRecordedDays(new Set());
          setCalendarSignals(new Map());
        }
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

  // 펫이 없으면(분양 직후 등) 0 으로 그린다.
  const stats = activePet;
  const spending = summary?.spending;
  const recent = summary?.recentExpenses ?? [];
  const activeTitle = titles.find((title) => title.active);
  const achievementPreview = (
    titles.some((title) => title.acquired)
      ? titles.filter((title) => title.acquired)
      : titles
  ).slice(0, 4);

  // 경험치바 — 프론트 임시 규칙: 스탯 4종 합 100당 1레벨, 나머지가 경험치.
  // 백엔드 exp 필드가 생기면 이 계산을 API 값으로 교체.
  const statTotal = STAT_META.reduce((s, m) => s + (stats?.[m.key] ?? 0), 0);
  const petLevel = Math.floor(statTotal / 100) + 1;
  const petExp = statTotal % 100;
  // 스탯 막대 기준값 — 가장 큰 스탯(최소 100). 스탯이 100을 넘어도 막대끼리 비교가 된다.
  const statScale = Math.max(100, ...STAT_META.map((m) => stats?.[m.key] ?? 0));

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
              {petLoaded && !activePet ? (
                /* 키우는 펫이 없을 때(분양 직후·알 개봉 전) — 마이룸과 같은 안내 */
                <div className="home-pet-empty">
                  <strong>아직 키우는 펫이 없어요</strong>
                  <p>상점에서 새 친구를 데려올 수 있어요.</p>
                  <button
                    type="button"
                    className="home-btn home-btn-primary"
                    onClick={() => navigate("/shop")}
                  >
                    상점 가기
                  </button>
                </div>
              ) : (
              <>
              <div className="home-pet-center">
                {/* 원형 경험치 게이지 — 270° 아치(아래 90° 열림)가 보리를 감싼다.
                    프론트 임시 규칙: 스탯 4종 합 100당 1레벨, 나머지가 경험치.
                    백엔드 exp 필드가 생기면 이 계산을 API 값으로 교체. */}
                <div className="home-pet-gauge">
                  {/* 그림 역할은 링에만 — 게이지 전체를 img 로 두면 안쪽 칭호 버튼이 보조기기에서 사라진다 */}
                  <svg
                    className="home-pet-gauge-ring"
                    viewBox="0 0 120 120"
                    role="img"
                    aria-label={`경험치 ${petExp}/100 (Lv. ${petLevel})`}
                  >
                    {/* 채움 색 — 화면 왼쪽(시작) 연한 세이지 → 오른쪽 짙은 세이지. 랜딩 톤과 맞춤.
                        원이 135° 회전돼 있어 좌표도 회전 전 기준(대각선)으로 잡았다. */}
                    <defs>
                      <linearGradient
                        id="home-pet-gauge-gradient"
                        gradientUnits="userSpaceOnUse"
                        x1="102" y1="102" x2="18" y2="18"
                      >
                        <stop offset="0%" stopColor="#c8dfaa" />
                        <stop offset="100%" stopColor="#7c9e6b" />
                      </linearGradient>
                    </defs>
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
                  {/* 펫을 불러오기 전에는 그림을 비워 둔다 — 기본 강아지가 잠깐 보였다 바뀌지 않게 */}
                  <div className="home-pet-art" aria-hidden>
                    {activePet && (
                      <PetArt
                        appearanceKey={activePet.appearanceKey}
                        stage={activePet.stage}
                        name=""
                        className="home-pet-image"
                        emojiClassName="home-pet-emoji"
                      />
                    )}
                  </div>
                  <button
                    type="button"
                    className="home-pet-title-badge"
                    onClick={() => navigate("/dex?tab=titles")}
                    aria-label={`칭호 ${activeTitle?.name ?? "없음"} — 칭호 도감 열기`}
                  >
                    {activeTitle?.name ?? "칭호 없음"}
                  </button>
                </div>
                <div className="home-pet-name-line">
                  <h2 className="home-pet-name">{activePet ? petDisplayName(activePet) : "보리"}</h2>
                  <span className="home-pet-level-label">Lv. {petLevel}</span>
                </div>
              </div>
              {/* 스탯 카드 — 흰 카드 + 수치 표시로 초록 배경 위에서도 눈에 띄게.
                  막대 길이는 네 스탯 중 가장 큰 값(최소 100) 기준 상대 비율. */}
              <section className="home-pet-stats home-statcard" aria-label="펫 스탯">
                <div className="home-statcard-head">
                  <h3 className="home-statcard-title">펫 스탯</h3>
                  <span className="home-statcard-total">
                    합계 <strong>{statTotal.toLocaleString("ko-KR")}</strong>
                  </span>
                </div>
                <ul className="home-stat-list">
                  {STAT_META.map((m) => {
                    const value = stats?.[m.key] ?? 0;
                    return (
                      <li
                        key={m.key}
                        className="home-stat-row"
                        style={{ "--stat-color": m.color }}
                      >
                        <span className="home-stat-label">{m.label}</span>
                        <div className="home-stat-track">
                          <div
                            className="home-stat-fill"
                            style={{ width: `${(Math.max(value, 0) / statScale) * 100}%` }}
                          />
                        </div>
                        <span className="home-stat-value">{value.toLocaleString("ko-KR")}</span>
                      </li>
                    );
                  })}
                </ul>
              </section>
              </>
              )}
            </div>
          </section>
        </div>

        {/* 지출 요약 카드 — 펫 카드와 같은 베이지 바탕에 카드마다 파스텔 포인트색(살구·하늘·세이지) */}
        <div className="home-row home-row-kpi">
          <article className="home-card home-kpi home-kpi--today">
            <div className="home-kpi-head">
              <h3 className="home-kpi-title">오늘 지출</h3>
            </div>
            <p className="home-kpi-value">{won(spending?.today)}</p>
          </article>
          <article className="home-card home-kpi home-kpi--month">
            <div className="home-kpi-head">
              <h3 className="home-kpi-title">이번 달 총 지출</h3>
            </div>
            <p className="home-kpi-value">{won(spending?.thisMonth)}</p>
          </article>
          <article className="home-card home-kpi home-kpi--week">
            <div className="home-kpi-head">
              <h3 className="home-kpi-title">이번 주 지출</h3>
            </div>
            <p className="home-kpi-value">{won(spending?.thisWeek)}</p>
          </article>
        </div>

        {/* 하단 카드 — 위 지출 요약 카드와 같은 베이지 바탕 + 파스텔 포인트(살구·하늘·세이지) */}
        <div className="home-row home-row-bottom">
          <section className="home-card home-card-list home-sec--list">
            <div className="home-list-head">
              <h2 className="home-card-title home-card-title--sm home-sec-title">
                <span className="home-sec-icon" aria-hidden>🧾</span>
                최근 지출 내역
              </h2>
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
                    <span className={`home-tx-icon home-tx-icon--${(row.signalFinal || "GRAY").toLowerCase()}`}>
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

          <section className="home-card home-card-achieve home-sec--achieve">
            <h2 className="home-card-title home-card-title--sm home-sec-title">
              <span className="home-sec-icon" aria-hidden>🏆</span>
              최근 업적
            </h2>
            <ul className="home-ach-list">
              {achievementPreview.length === 0 ? (
                <li className="home-ach-row">
                  {titlesLoading ? "칭호 정보를 불러오는 중..." : "아직 표시할 업적이 없어요."}
                </li>
              ) : (
                achievementPreview.map((title) => (
                  <li key={title.code} className="home-ach-row">
                    <div className="home-ach-mid">
                      <span className="home-ach-title">{title.name}</span>
                      {/* 진행 중인 업적은 진행률 막대를 같이 보여준다 */}
                      {!title.acquired && (
                        <span className="home-ach-progress" aria-hidden>
                          <span style={{ width: `${Math.min(Math.max(title.progressPct ?? 0, 0), 100)}%` }} />
                        </span>
                      )}
                    </div>
                    <span
                      className={`home-badge ${title.acquired ? "home-badge--done" : "home-badge--prog"}`}
                    >
                      {title.acquired ? "✓ 완료" : `${title.progressPct}%`}
                    </span>
                  </li>
                ))
              )}
            </ul>
            <button
              type="button"
              className="home-btn home-btn-primary home-btn-block"
              onClick={() => navigate("/dex?tab=achievements")}
            >
              ▶ 더 많은 업적 확인하기
            </button>
          </section>

          <section className="home-card home-card-chart home-sec--calendar">
            <h2 className="home-card-title home-card-title--sm home-sec-title">
              <span className="home-sec-icon" aria-hidden>📅</span>
              {today.getMonth() + 1}월 기록 캘린더
            </h2>
            <div className="home-cal">
              <RecordCalendar
                year={today.getFullYear()}
                month={today.getMonth() + 1}
                recordedKeys={recordedDays}
                signalByKey={calendarSignals}
              />
            </div>
            <button
              type="button"
              className="home-btn home-btn-primary home-btn-block"
              onClick={() => navigate("/report")}
            >
              ▶ 리포트 확인하기
            </button>
          </section>
        </div>

        <p className="home-footnote">
          {user?.role === "ADMIN"
            ? "관리자는 언제든지 소비 판정을 확인할 수 있어요"
            : `매일 ${AI_ACTIVE_FROM_HOUR}시에 보리가 소비 검사를 시작해요`}
        </p>
      </main>

      <AppRightSidebar />
    </AppShell>
  );
}

function buildSignalMap(rows) {
  const rank = { GREEN: 1, GRAY: 2, RED: 3 };
  const result = new Map();
  rows.forEach((row) => {
    if (row.type !== "EXPENSE" || !row.signal) return;
    const [y, m, d] = row.date.split("-").map(Number);
    const key = dateKey(y, m, d);
    const current = result.get(key);
    if (!current || rank[row.signal] > rank[current]) result.set(key, row.signal);
  });
  return result;
}

export default HomeDashboard;
