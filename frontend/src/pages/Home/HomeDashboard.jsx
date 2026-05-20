import "./HomeDashboard.css";

const STATS = [
  { key: "energy", label: "에너지", value: 68, color: "var(--home-bar-green)" },
  { key: "charm", label: "매력", value: 68, color: "var(--home-bar-red)" },
  { key: "int", label: "지능", value: 68, color: "var(--home-bar-orange)" },
  { key: "sta", label: "지구력", value: 68, color: "var(--home-bar-blue)" },
];

const TX_ROWS = [
  { icon: "🎬", name: "Netflix", cat: "고정비", amount: "17,000원" },
  { icon: "🍱", name: "점심 식사", cat: "식비", amount: "12,500원" },
  { icon: "☕", name: "스타벅스", cat: "식비", amount: "3,000원" },
];

const ACHIEVEMENTS = [
  { title: "첫 지출 기록", status: "완료", done: true },
  { title: "한 주 예산 지키기", status: "진행중", done: false },
  { title: "카페 지출 줄이기", status: "진행중", done: false },
  { title: "업적 10개 달성", status: "완료", done: true },
];

const CATEGORY_BARS = [
  { label: "식비", pct: 42, amount: "146,200원", color: "var(--home-bar-green)" },
  { label: "쇼핑", pct: 22, amount: "76,500원", color: "var(--home-bar-red)" },
  { label: "문화", pct: 18, amount: "62,600원", color: "var(--home-bar-orange)" },
  { label: "고정비", pct: 18, amount: "62,600원", color: "var(--home-bar-blue)" },
];

function HomeDashboard({ user, onNavigate, onLogout }) {
  const nickname = user?.nickname || "사용자";
  const go = (page) => {
    if (typeof onNavigate === "function") onNavigate(page);
  };

  const today = new Date();
  const dateStr = new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "long",
  }).format(today);

  const hour = today.getHours();
  const greeting =
    hour < 11
      ? "좋은 아침이에요!"
      : hour < 18
        ? "좋은 오후예요!"
        : "좋은 저녁이에요!";

  return (
    <div className="home">
      <header className="home-topbar">
        <div className="home-topbar-inner">
          <button
            type="button"
            className="home-logo"
            onClick={() => go("home")}
            aria-label="VORI 홈"
          >
            <span className="home-logo-vo">VO</span>
            <span className="home-logo-ri">RI</span>
          </button>
          <nav className="home-topnav" aria-label="주 메뉴">
            {[
              { id: "home", label: "홈" },
              { id: "ledger", label: "가계부" },
              { id: "room", label: "마이룸" },
              { id: "shop", label: "상점" },
              { id: "raise", label: "키우기" },
            ].map((item) => (
              <button
                key={item.id}
                type="button"
                className={`home-topnav-item ${item.id === "home" ? "is-active" : ""}`}
                onClick={() => {
                  if (item.id === "home") go("home");
                }}
              >
                {item.label}
              </button>
            ))}
          </nav>
          <button
            type="button"
            className="home-logout-link"
            onClick={() => {
              if (typeof onLogout === "function") onLogout();
            }}
          >
            로그아웃
          </button>
        </div>
      </header>

      <div className="home-shell">
        <aside
          className="home-sidebar home-sidebar--left"
          aria-label="사이드 메뉴"
        >
          <div className="home-side-block">
            <div className="home-side-title">메뉴</div>
            <ul className="home-side-list">
              <li>
                <button type="button" className="home-side-link is-active">
                  홈 대시보드
                </button>
              </li>
              <li>
                <button type="button" className="home-side-link">
                  가계부 달력
                </button>
              </li>
              <li>
                <button type="button" className="home-side-link">
                  소비 리포트
                </button>
              </li>
            </ul>
          </div>
          <div className="home-side-block">
            <div className="home-side-title">게임</div>
            <ul className="home-side-list">
              <li>
                <button type="button" className="home-side-link">
                  펫 키우기
                </button>
              </li>
              <li>
                <button type="button" className="home-side-link">
                  마이룸
                </button>
              </li>
              <li>
                <button type="button" className="home-side-link">
                  상점
                </button>
              </li>
              <li>
                <button type="button" className="home-side-link">
                  업적/칭호
                </button>
              </li>
            </ul>
          </div>
          <div className="home-side-block">
            <div className="home-side-title">설정</div>
            <ul className="home-side-list">
              <li>
                <button type="button" className="home-side-link">
                  환경설정
                </button>
              </li>
              <li>
                <button
                  type="button"
                  className="home-side-link"
                  onClick={() => {
                    if (typeof onLogout === "function") onLogout();
                  }}
                >
                  로그아웃
                </button>
              </li>
            </ul>
          </div>
        </aside>

        <main className="home-main">
          <div className="home-main-header">
            <h1 className="home-greeting">
              {greeting} {nickname}님, 오늘도 보리와 함께해요!
            </h1>
            <p className="home-date">{dateStr}</p>
          </div>

          <div className="home-row home-row-pet">
            <section className="home-card home-card-pet">
              <p className="home-pet-stage">청소년기 · 성체까지 00일 남음</p>
              <div className="home-pet-head">
                <div>
                  <h2 className="home-pet-name">보리</h2>
                  <p className="home-pet-status">상태 : 배고픔</p>
                </div>
                <div className="home-pet-art" aria-hidden>
                  <span className="home-pet-pixel">🐕</span>
                </div>
              </div>
              <div className="home-pet-bubble">
                어제 카페를 두 번이나 갔군요! 오늘은 아메리카노 한 잔만 해요
              </div>
            </section>

            <section className="home-card home-card-stats">
              <h2 className="home-card-title">스탯 현황</h2>
              <ul className="home-stat-list">
                {STATS.map((s) => (
                  <li key={s.key} className="home-stat-row">
                    <span className="home-stat-label">{s.label}</span>
                    <div className="home-stat-track">
                      <div
                        className="home-stat-fill"
                        style={{ width: `${s.value}%`, background: s.color }}
                      />
                    </div>
                    <span className="home-stat-num">{s.value}</span>
                  </li>
                ))}
              </ul>
            </section>
          </div>

          <div className="home-row home-row-kpi">
            <article className="home-card home-kpi">
              <h3 className="home-kpi-title">오늘 지출</h3>
              <p className="home-kpi-value">32,500원</p>
              <p className="home-kpi-sub home-kpi-sub--warn">
                목표 대비 +12% 초과
              </p>
            </article>
            <article className="home-card home-kpi">
              <h3 className="home-kpi-title">이번 달 누적</h3>
              <p className="home-kpi-value">348,000원</p>
              <p className="home-kpi-sub">예산 내 유지 중</p>
            </article>
            <article className="home-card home-kpi">
              <h3 className="home-kpi-title">이번 주 지출</h3>
              <p className="home-kpi-value">124,800원</p>
              <p className="home-kpi-sub home-kpi-sub--warn">
                지난 주 대비 +8,200원
              </p>
            </article>
          </div>

          <div className="home-row home-row-bottom">
            <section className="home-card home-card-list">
              <h2 className="home-card-title home-card-title--sm">
                최근 지출 내역
              </h2>
              <p className="home-list-date">5월 12일 (목)</p>
              <ul className="home-tx-list">
                {TX_ROWS.map((row) => (
                  <li key={row.name} className="home-tx-row">
                    <span className="home-tx-icon">{row.icon}</span>
                    <div className="home-tx-mid">
                      <span className="home-tx-name">{row.name}</span>
                      <span className="home-tx-cat">{row.cat}</span>
                    </div>
                    <span className="home-tx-amount">{row.amount}</span>
                  </li>
                ))}
              </ul>
              <div className="home-card-actions">
                <button type="button" className="home-link-btn">
                  ▶ 상세 내역 확인하기
                </button>
                <button type="button" className="home-btn home-btn-dark">
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
                카테고리별 지출
              </h2>
              <div className="home-cat-chart">
                {CATEGORY_BARS.map((c) => (
                  <div key={c.label} className="home-cat-row">
                    <span className="home-cat-label">{c.label}</span>
                    <div className="home-cat-track">
                      <div
                        className="home-cat-fill"
                        style={{ width: `${c.pct}%`, background: c.color }}
                      />
                    </div>
                    <span className="home-cat-amount">{c.amount}</span>
                  </div>
                ))}
              </div>
              <button type="button" className="home-btn home-btn-primary home-btn-block">
                ▶ 리포트 확인하기
              </button>
            </section>
          </div>

          <p className="home-footnote">
            매일 오후 8시에 보리가 소비 검사를 시작해요
          </p>
        </main>

        <aside
          className="home-sidebar home-sidebar--right"
          aria-label="보조 사이드바"
        >
          <div className="home-side-block">
            <div className="home-side-title">요약</div>
            <ul className="home-side-list">
              <li>
                <span className="home-side-static">오늘 기록 3건</span>
              </li>
              <li>
                <span className="home-side-static">이번 주 목표까지 12%</span>
              </li>
            </ul>
          </div>
          <div className="home-side-block">
            <div className="home-side-title">바로가기</div>
            <ul className="home-side-list">
              <li>
                <button type="button" className="home-side-link">
                  지출 입력
                </button>
              </li>
              <li>
                <button type="button" className="home-side-link">
                  예산 설정
                </button>
              </li>
            </ul>
          </div>
        </aside>
      </div>
    </div>
  );
}

export default HomeDashboard;
