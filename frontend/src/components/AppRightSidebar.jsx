import { useNavigate } from "react-router-dom";

// 우측 보조 사이드바.
// 이전의 "요약"(오늘 기록 N건·목표 %)은 데이터 소스 없는 하드코딩이라 내렸다 —
// 실데이터 API 가 생기면 그때 되살린다. 바로가기는 실제 라우트만 노출.
function AppRightSidebar() {
  const navigate = useNavigate();
  return (
    <aside
      className="home-sidebar home-sidebar--right"
      aria-label="보조 사이드바"
    >
      <div className="home-side-block">
        <div className="home-side-title">바로가기</div>
        <ul className="home-side-list">
          <li>
            <button
              type="button"
              className="home-side-link"
              onClick={() => navigate("/wallet/new")}
            >
              지출 입력
            </button>
          </li>
          <li>
            <button type="button" className="home-side-link" disabled>
              예산 설정
              <span className="home-side-soon">준비 중</span>
            </button>
          </li>
        </ul>
      </div>
    </aside>
  );
}

export default AppRightSidebar;
