// 답변 사유 칩 — 서버 ReasonCategory 와 같은 값. 문구는 판정 문구처럼 중립으로(병원비·장례비에도 나간다).
// 칩만 고르면 서버가 같은 문구(ReasonRules.CHIP_LABEL)를 답변으로 남긴다.
export const REASON_CHIPS = [
  { value: "CEREMONY", label: "경조사" },
  { value: "EMERGENCY", label: "갑작스러운 일" },
  { value: "SOCIAL", label: "모임·만남" },
  { value: "SELF_INVEST", label: "배움·자기계발" },
  { value: "IMPULSE", label: "사고 싶어서" },
  { value: "ETC", label: "기타" },
];
