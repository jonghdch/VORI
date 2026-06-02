// 어드민 사이드바 네비게이션 구성.
// 그룹 라벨 + 그 아래 항목들. 각 항목은 NavLink 의 to 경로를 가진다.
// 화면(스크린샷)의 사이드바 구조를 그대로 반영. 메인 콘텐츠는 페이지별로 별도.
export const ADMIN_NAV = [
  {
    label: "기본 관리",
    items: [
      { to: "/admin/dashboard", label: "종합 대시보드" },
      { to: "/admin/users", label: "유저 현황" },
      { to: "/admin/items", label: "아이템 지급/회수" },
      { to: "/admin/sanctions", label: "제재 관리" },
    ],
  },
  {
    label: "상점 및 경제 시스템",
    items: [
      { to: "/admin/shop-stages", label: "상점 단계 관리" },
      { to: "/admin/products", label: "상품 관리" },
      { to: "/admin/gacha", label: "뽑기 확률 설정" },
      { to: "/admin/currency-log", label: "재화 로그" },
    ],
  },
  {
    label: "게임 컨텐츠",
    items: [
      { to: "/admin/pets", label: "펫 / 도감 관리" },
      { to: "/admin/cosmetics", label: "꾸미기 컨텐츠 관리" },
    ],
  },
  {
    label: "소비 분석 AI 및 데이터",
    items: [
      { to: "/admin/category-stats", label: "지출 카테고리 통계" },
      { to: "/admin/rationality-rules", label: "합리성 판정 / AI 룰 설정" },
      { to: "/admin/ai-logs", label: "AI 대사 로그" },
      { to: "/admin/ocr-jobs", label: "영수증 OCR 모니터링" },
    ],
  },
  {
    label: "시스템 및 컨텐츠 운영",
    items: [
      { to: "/admin/achievements", label: "업적 / 이벤트 관리" },
      { to: "/admin/notices", label: "공지사항 및 푸시 알림" },
      { to: "/admin/audit-log", label: "운영 감사 로그" },
    ],
  },
];

// to 경로 → 화면 제목 빠른 조회용 (placeholder 헤딩에서 사용).
export const ADMIN_TITLES = ADMIN_NAV.reduce((acc, group) => {
  group.items.forEach((item) => {
    acc[item.to] = item.label;
  });
  return acc;
}, {});
