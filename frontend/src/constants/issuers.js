// 카드사·은행 고정 목록. 로고 이미지는 각 회사 상표라 쓰지 않고, 이니셜 + 대표색 원형으로 구분한다.
// code 는 나중에 백엔드 payment_instrument.issuer 컬럼에 그대로 저장할 값이다.
export const ISSUERS = [
  { code: "KB", name: "KB국민", initial: "KB", color: "#f2b705" },
  { code: "SHINHAN", name: "신한", initial: "신", color: "#2a5bd7" },
  { code: "SAMSUNG", name: "삼성", initial: "삼", color: "#1b3f94" },
  { code: "HYUNDAI", name: "현대", initial: "현", color: "#3a3a3a" },
  { code: "LOTTE", name: "롯데", initial: "롯", color: "#d8262f" },
  { code: "WOORI", name: "우리", initial: "우", color: "#1f6fc5" },
  { code: "HANA", name: "하나", initial: "하", color: "#00857a" },
  { code: "BC", name: "BC", initial: "BC", color: "#e0464b" },
  { code: "NH", name: "NH농협", initial: "농", color: "#1b9e4b" },
  { code: "IBK", name: "IBK기업", initial: "기", color: "#0b5aa8" },
  { code: "KAKAOBANK", name: "카카오뱅크", initial: "카", color: "#f7d300" },
  { code: "TOSSBANK", name: "토스뱅크", initial: "토", color: "#2272eb" },
  { code: "KBANK", name: "케이뱅크", initial: "K", color: "#1c1c1c" },
  { code: "POST", name: "우체국", initial: "우체", color: "#e4572e" },
  { code: "SAEMAEUL", name: "새마을금고", initial: "새", color: "#2b6cb0" },
  { code: "ETC", name: "기타", initial: "·", color: "#9c9488" },
];

export const issuerOf = (code) => ISSUERS.find((i) => i.code === code) || ISSUERS[ISSUERS.length - 1];

// 등록 종류. paymentMethod 는 이 결제수단으로 적힌 지출의 기존 결제수단 enum 이다.
// (지출에 결제수단 id 가 아직 없어서, 프로토타입에서는 이 enum 으로 지출을 묶는다.)
export const INSTRUMENT_KINDS = {
  DEBIT: { label: "체크카드", group: "CARD", paymentMethod: "DEBIT" },
  CREDIT: { label: "신용카드", group: "CARD", paymentMethod: "CREDIT" },
  CHECKING: { label: "입출금 계좌", group: "ACCOUNT", paymentMethod: "TRANSFER" },
  SAVINGS: { label: "예적금", group: "SAVINGS", paymentMethod: null },
  CASH: { label: "현금", group: "ACCOUNT", paymentMethod: "CASH" },
};
