function AppRightSidebar() {
  return (
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
  );
}

export default AppRightSidebar;
