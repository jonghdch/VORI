import { useLocation } from "react-router-dom";
import { ADMIN_TITLES } from "./adminNav";

// 각 어드민 메뉴의 임시 본문. 사이드바만 먼저 구축한 단계라 본문은 빈 상태.
// 메뉴별 실제 화면이 준비되면 이 컴포넌트를 개별 페이지로 교체.
function AdminPlaceholder() {
  const { pathname } = useLocation();
  const title = ADMIN_TITLES[pathname] || "어드민";

  return (
    <div className="admin-placeholder">
      <h1 className="admin-placeholder-title">{title}</h1>
      <p className="admin-placeholder-sub">{title} 화면입니다.</p>
      <div className="admin-placeholder-card">
        <span className="admin-placeholder-empty">준비 중입니다.</span>
      </div>
    </div>
  );
}

export default AdminPlaceholder;
