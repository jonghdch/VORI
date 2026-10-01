import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getMe } from "../api/user";
import { AI_ACTIVE_FROM_HOUR, canUseAiJudge } from "../config";

// 우측 보조 사이드바.
// 이전의 "요약"(오늘 기록 N건·목표 %)은 데이터 소스 없는 하드코딩이라 내렸다 —
// 실데이터 API 가 생기면 그때 되살린다. 바로가기는 실제 라우트만 노출.
function AppRightSidebar() {
  const navigate = useNavigate();

  // 소비 판정은 가계부 카드와 같은 기준으로 연다 — 관리자는 언제나, 일반 계정은 AI_ACTIVE_FROM_HOUR 부터.
  // 시각이 바뀌면 버튼도 따라가게 1분마다 다시 본다.
  const [me, setMe] = useState(null);
  const [judgeOpen, setJudgeOpen] = useState(() => canUseAiJudge(null));
  useEffect(() => {
    let alive = true;
    getMe()
      .then((m) => alive && setMe(m))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  useEffect(() => {
    const check = () => setJudgeOpen(canUseAiJudge(me));
    check();
    const timer = setInterval(check, 60_000);
    return () => clearInterval(timer);
  }, [me]);

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
              <span className="home-side-icon" aria-hidden />
              <span className="home-side-label">지출 입력</span>
            </button>
          </li>
          <li>
            <button
              type="button"
              className="home-side-link"
              disabled={!judgeOpen}
              onClick={() => navigate("/wallet/analysis")}
              title={judgeOpen ? undefined : `${AI_ACTIVE_FROM_HOUR}시부터 열려요`}
            >
              <span className="home-side-icon" aria-hidden />
              <span className="home-side-label">소비 판정 시작하기</span>
              {!judgeOpen && <span className="home-side-soon">{AI_ACTIVE_FROM_HOUR}시부터</span>}
            </button>
          </li>
          <li>
            <button type="button" className="home-side-link" disabled>
              <span className="home-side-icon" aria-hidden />
              <span className="home-side-label">예산 설정</span>
              <span className="home-side-soon">준비 중</span>
            </button>
          </li>
        </ul>
      </div>
    </aside>
  );
}

export default AppRightSidebar;
