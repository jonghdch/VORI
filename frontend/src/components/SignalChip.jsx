import Icon from "./Icon";
import { SIGNAL_STATUS } from "../signalLabels";

// 판정 표시 — 색만으로 구분하지 않도록 아이콘 + 글자(여유·적정·초과)를 늘 함께 쓴다.
// 홈·가계부가 같이 쓴다. 칩 모양은 pages/Home/HomeScreen.css 의 .hm-signal.
export const SIGNAL_META = {
  GREEN: { tone: "green", icon: "checkCircle", text: SIGNAL_STATUS.GREEN },
  GRAY: { tone: "amber", icon: "minusCircle", text: SIGNAL_STATUS.GRAY },
  RED: { tone: "red", icon: "alertCircle", text: SIGNAL_STATUS.RED },
};

/** 사용률 → 판정 신호. 서버 신호등 기준과 같다: 85% 이하 여유, 100% 이하 적정, 넘으면 초과. */
export function signalForUsage(spent, allowance) {
  if (allowance <= 0) return spent > 0 ? "RED" : "GRAY";
  const pct = (spent / allowance) * 100;
  if (pct <= 85) return "GREEN";
  if (pct <= 100) return "GRAY";
  return "RED";
}

// 카테고리 이름 → 아이콘. 백엔드가 아이콘을 주지 않아 낱말로 고른다. 못 고르면 영수증.
const CATEGORY_ICON_RULES = [
  [/카페|커피|음료/, "cafe"],
  [/식|외식|배달|편의점|마트/, "meal"],
  [/교통|버스|택시|주유|주차|지하철/, "bus"],
  [/쇼핑|의류|신발|가방|뷰티|화장|향수|헤어|네일|액세서리|잡화/, "bag"],
  [/문화|영화|공연|전시|도서|게임|취미|여행|여가|스포츠|헬스|구독/, "ticket"],
  [/경조사|선물|축의|부의/, "gift"],
];
export const categoryIcon = (name = "") =>
  CATEGORY_ICON_RULES.find(([re]) => re.test(name))?.[1] ?? "receipt";

function SignalChip({ signal, size = "sm", label }) {
  const meta = SIGNAL_META[signal];
  if (!meta) return null;
  return (
    <span className={`hm-signal hm-signal--${meta.tone} hm-signal--${size}`}>
      <Icon name={meta.icon} size={size === "lg" ? 16 : 13} strokeWidth={2.2} />
      {label ?? meta.text}
    </span>
  );
}

export default SignalChip;
