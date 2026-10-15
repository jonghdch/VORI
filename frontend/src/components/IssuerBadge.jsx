import { issuerOf } from "../constants/issuers";
import "./IssuerBadge.css";

// 카드사·은행 표시 — 상표 로고 대신 이니셜 + 대표색 원형.
function IssuerBadge({ issuer, size = 28 }) {
  const meta = issuerOf(issuer);
  return (
    <span
      className="issuer-badge"
      style={{ background: meta.color, width: size, height: size, fontSize: size * 0.38 }}
      aria-hidden
    >
      {meta.initial}
    </span>
  );
}

export default IssuerBadge;
