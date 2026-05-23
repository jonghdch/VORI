// ──────────────────────────────────────────────
// 날짜 헬퍼
// ──────────────────────────────────────────────

export function formatToday(date = new Date()) {
  const y = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  const day = ["일", "월", "화", "수", "목", "금", "토"][date.getDay()];
  return `${y}.${mm}.${dd} (${day}요일)`;
}

export function toIsoDate(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function parseIsoDate(s) {
  if (!s || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return new Date();
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function isPastDate(dateStr) {
  if (!dateStr) return false;
  return dateStr < toIsoDate();
}

// ──────────────────────────────────────────────
// 결제수단·카테고리 카탈로그
// ──────────────────────────────────────────────

// 백엔드 V2 expenses.payment_method ENUM 과 1:1
export const PAYMENT_METHODS = [
  { value: "CASH", label: "현금" },
  { value: "DEBIT", label: "체크카드" },
  { value: "CREDIT", label: "신용카드" },
  { value: "TRANSFER", label: "계좌이체" },
  { value: "MOBILE_PAY", label: "모바일페이" },
];

// 카테고리 트리.
// - expense: 대분류 → 소분류 2단계. 백엔드 categories 테이블의 parent_id 트리와 매핑.
// - income, savings: 항목 수가 적어 평면 유지.
// - 각 노드: { value, label, children? }
export const CATEGORIES_BY_TYPE = {
  expense: [
    {
      value: "FOOD",
      label: "식비",
      children: [
        { value: "FOOD_KOREAN", label: "한식" },
        { value: "FOOD_JAPANESE", label: "일식" },
        { value: "FOOD_CHINESE", label: "중식" },
        { value: "FOOD_WESTERN", label: "양식" },
        { value: "FOOD_CAFE", label: "카페" },
        { value: "FOOD_CONVENIENCE", label: "편의점" },
        { value: "FOOD_FAST", label: "패스트푸드" },
        { value: "FOOD_DELIVERY", label: "배달" },
        { value: "FOOD_ETC", label: "기타" },
      ],
    },
    {
      value: "TRANSPORT",
      label: "교통",
      children: [
        { value: "TRANSPORT_SUBWAY", label: "지하철" },
        { value: "TRANSPORT_BUS", label: "버스" },
        { value: "TRANSPORT_TAXI", label: "택시" },
        { value: "TRANSPORT_FUEL", label: "주유" },
        { value: "TRANSPORT_TRAIN", label: "기차" },
        { value: "TRANSPORT_AIR", label: "비행기" },
        { value: "TRANSPORT_ETC", label: "기타" },
      ],
    },
    {
      value: "SHOPPING",
      label: "쇼핑",
      children: [
        { value: "SHOPPING_ONLINE", label: "온라인" },
        { value: "SHOPPING_CLOTHES", label: "의류" },
        { value: "SHOPPING_BEAUTY", label: "화장품" },
        { value: "SHOPPING_MART", label: "마트" },
        { value: "SHOPPING_ETC", label: "기타" },
      ],
    },
    {
      value: "CULTURE",
      label: "문화",
      children: [
        { value: "CULTURE_MOVIE", label: "영화" },
        { value: "CULTURE_CONCERT", label: "공연" },
        { value: "CULTURE_BOOK", label: "도서" },
        { value: "CULTURE_SUBSCRIPTION", label: "구독" },
        { value: "CULTURE_ETC", label: "기타" },
      ],
    },
    {
      value: "LIVING",
      label: "생활",
      children: [
        { value: "LIVING_COMM", label: "통신" },
        { value: "LIVING_UTILITY", label: "관리비" },
        { value: "LIVING_RENT", label: "주거" },
        { value: "LIVING_HEALTH", label: "의료" },
        { value: "LIVING_BEAUTY", label: "미용" },
        { value: "LIVING_ETC", label: "기타" },
      ],
    },
    { value: "ETC", label: "기타" }, // 어떤 대분류에도 안 들어가는 fallback
  ],
  // 백엔드 IncomeSource ENUM 과 1:1
  income: [
    { value: "ALLOWANCE", label: "용돈" },
    { value: "PART_TIME", label: "알바" },
    { value: "SCHOLARSHIP", label: "장학금" },
    { value: "SIDE_JOB", label: "부수입" },
    { value: "GIFT", label: "선물" },
    { value: "INTEREST", label: "이자" },
    { value: "OTHER", label: "기타" },
  ],
  // 백엔드 SavingType ENUM 과 1:1
  savings: [
    { value: "DEPOSIT", label: "예금" },
    { value: "INVEST", label: "투자" },
  ],
};

// 트리에서 value 가 일치하는 노드 찾기. leaf·root 둘 다 검색.
export function findCategory(value, type) {
  if (!value) return null;
  const tree = CATEGORIES_BY_TYPE[type] || [];
  for (const node of tree) {
    if (node.value === value) return node;
    if (node.children) {
      const leaf = node.children.find((c) => c.value === value);
      if (leaf) return leaf;
    }
  }
  return null;
}

// leaf value → 부모 대분류 노드 (없으면 자기 자신, root 면 null)
export function findMajor(value, type) {
  if (!value) return null;
  const tree = CATEGORIES_BY_TYPE[type] || [];
  for (const node of tree) {
    if (node.value === value) return node; // root 자체
    if (node.children && node.children.some((c) => c.value === value)) {
      return node;
    }
  }
  return null;
}

export function getCategoryLabel(value, type) {
  return findCategory(value, type)?.label || null;
}

// "대분류 · 소분류" 표시용. 트리가 평면(income/savings)이거나 root 자체(ETC)면 leaf 만.
export function getCategoryDisplayPath(value, type) {
  const leaf = findCategory(value, type);
  const major = findMajor(value, type);
  if (!leaf) return null;
  if (!major || major.value === leaf.value) return leaf.label;
  return `${major.label} · ${leaf.label}`;
}

// ──────────────────────────────────────────────
// 자동 분류 (client-side keyword rules)
// 룰은 항상 leaf(소분류) 를 가리킴. 대분류는 트리에서 lookup.
// 위 순서대로 우선. 최초 매칭 즉시 return.
// 추후 백엔드 POST /api/categorize 로 교체 — 시그니처 유지.
// ──────────────────────────────────────────────

const EXPENSE_RULES = [
  // ─── 식비 ───
  {
    value: "FOOD_CAFE",
    keywords: [
      "스타벅스", "스벅", "투썸", "이디야", "폴바셋", "메가커피", "메가",
      "빽다방", "컴포즈", "할리스", "엔젤리너스", "탐앤탐스",
      "카페", "라떼", "아메리카노", "에스프레소", "커피",
    ],
  },
  {
    value: "FOOD_CONVENIENCE",
    keywords: ["gs25", "cu", "세븐일레븐", "세븐", "이마트24", "미니스톱", "편의점"],
  },
  {
    value: "FOOD_FAST",
    keywords: ["맥도날드", "버거킹", "롯데리아", "kfc", "서브웨이", "맘스터치", "햄버거", "피자"],
  },
  {
    value: "FOOD_JAPANESE",
    keywords: [
      "초밥", "스시", "사시미", "회",
      "돈까스", "라멘", "우동", "규동", "가츠동", "오뎅", "오꼬노미야끼", "타코야끼",
      "일식", "이자카야",
    ],
  },
  {
    value: "FOOD_CHINESE",
    keywords: [
      "짜장", "짬뽕", "탕수육", "깐풍기", "양꼬치", "훠궈", "마라탕", "마라", "마파두부",
      "중식", "중국집",
    ],
  },
  {
    value: "FOOD_WESTERN",
    keywords: ["파스타", "스테이크", "리조또", "스튜", "샐러드볼", "양식"],
  },
  {
    value: "FOOD_DELIVERY",
    keywords: ["배민", "배달의민족", "쿠팡이츠", "요기요", "배달"],
  },
  // 한식의 "국수" 같은 광역 단어가 잡아채지 않도록 위로 올림
  {
    value: "FOOD_ETC",
    keywords: ["쌀국수", "팟타이", "분짜", "똠얌", "케밥", "타코"],
  },
  {
    value: "FOOD_KOREAN",
    keywords: [
      "식당", "분식", "김밥", "한식", "한정식",
      "치킨", "양념치킨", "후라이드",
      "도시락", "백반", "국밥", "라면", "떡볶이", "김치찌개", "찌개",
      "비빔밥", "불고기", "삼겹살", "갈비", "냉면", "쌈밥", "국수",
    ],
  },

  // ─── 교통 ───
  { value: "TRANSPORT_SUBWAY", keywords: ["지하철"] },
  { value: "TRANSPORT_BUS", keywords: ["버스", "고속버스"] },
  { value: "TRANSPORT_TAXI", keywords: ["택시", "카카오t", "카카오택시", "우버", "따릉이"] },
  { value: "TRANSPORT_FUEL", keywords: ["주유", "기름", "휘발유", "경유", "톨게이트", "하이패스"] },
  { value: "TRANSPORT_TRAIN", keywords: ["ktx", "srt", "기차"] },
  { value: "TRANSPORT_AIR", keywords: ["비행기", "항공"] },

  // ─── 쇼핑 ───
  {
    value: "SHOPPING_ONLINE",
    keywords: ["쿠팡", "11번가", "g마켓", "옥션", "위메프", "티몬", "네이버쇼핑", "네이버쇼", "스마트스토어"],
  },
  {
    value: "SHOPPING_CLOTHES",
    keywords: ["무신사", "자라", "유니클로", "h&m", "spao", "탑텐"],
  },
  {
    value: "SHOPPING_BEAUTY",
    keywords: ["다이소", "올리브영", "올영"],
  },
  {
    value: "SHOPPING_MART",
    keywords: ["이마트", "홈플러스", "롯데마트", "코스트코"],
  },

  // ─── 문화 ───
  { value: "CULTURE_MOVIE", keywords: ["영화", "cgv", "메가박스", "롯데시네마"] },
  { value: "CULTURE_CONCERT", keywords: ["콘서트", "공연", "전시", "박물관", "미술관"] },
  { value: "CULTURE_BOOK", keywords: ["도서", "교보문고", "알라딘", "예스24", "책"] },
  {
    value: "CULTURE_SUBSCRIPTION",
    keywords: [
      "넷플릭스", "디즈니", "왓챠", "티빙", "웨이브",
      "멜론", "지니", "벅스", "스포티파이",
      "유튜브 프리미엄", "유튜브프리미엄", "유튜브",
    ],
  },

  // ─── 생활 ───
  {
    value: "LIVING_COMM",
    keywords: ["통신", "kt", "skt", "lg u+", "lgu+", "인터넷", "wifi"],
  },
  {
    value: "LIVING_UTILITY",
    keywords: ["전기", "한전", "가스", "수도", "관리비"],
  },
  {
    value: "LIVING_RENT",
    keywords: ["월세", "전세"],
  },
  {
    value: "LIVING_HEALTH",
    keywords: ["약국", "병원", "치과", "한의원", "처방"],
  },
  {
    value: "LIVING_BEAUTY",
    keywords: ["미용실", "헤어", "네일", "왁싱"],
  },
];

const INCOME_RULES = [
  { value: "ALLOWANCE", keywords: ["용돈", "부모님", "엄마", "아빠", "아버지", "어머니"] },
  { value: "PART_TIME", keywords: ["알바", "아르바이트", "시급"] },
  { value: "SCHOLARSHIP", keywords: ["장학금"] },
  {
    value: "SIDE_JOB",
    keywords: ["과외", "외주", "프리랜서", "부업", "원고료", "강의", "튜터"],
  },
  { value: "GIFT", keywords: ["선물", "축의금", "부조금"] },
  { value: "INTEREST", keywords: ["이자", "배당"] },
  // 월급/급여는 학생 가계부 enum 에 별도 카테고리 없음 → OTHER
  { value: "OTHER", keywords: ["월급", "급여", "봉급", "페이"] },
];

const SAVINGS_RULES = [
  { value: "DEPOSIT", keywords: ["적금", "예금", "저축", "통장", "cma"] },
  {
    value: "INVEST",
    keywords: [
      "주식", "펀드", "etf", "코인", "비트", "비트코인", "이더", "이더리움",
      "가상화폐", "채권", "리츠",
    ],
  },
];

const RULES_BY_TYPE = {
  expense: EXPENSE_RULES,
  income: INCOME_RULES,
  savings: SAVINGS_RULES,
};

// 매칭 실패 시 타입별 fallback (백엔드 enum 과 호환되는 값으로).
const FALLBACK_BY_TYPE = {
  expense: "ETC",
  income: "OTHER",
  savings: "DEPOSIT",
};

// name 의 의도를 보고 카테고리 추론.
// 매칭 시 leaf value 반환. 실패 시 type 별 fallback, 빈 입력 시 null.
export function categorize(name, type) {
  if (!name || !name.trim()) return null;
  const haystack = name.trim().toLowerCase();
  const rules = RULES_BY_TYPE[type] || [];
  for (const { value, keywords } of rules) {
    if (keywords.some((k) => haystack.includes(k.toLowerCase()))) {
      return value;
    }
  }
  return FALLBACK_BY_TYPE[type] || "ETC";
}
