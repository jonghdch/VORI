import { useNavigate } from "react-router-dom";
import "./LegalPages.css";

/**
 * 법적 문서(이용약관·개인정보처리방침) 공용 레이아웃 — 공개 페이지.
 * VORI 로고 헤더 + 본문 타이포. 자식은 <Section title>, <P>, <Ul> 로 구성한다.
 */
export default function LegalLayout({ title, effectiveDate, children }) {
  const navigate = useNavigate();
  return (
    <div className="legal-page">
      <header className="legal-header">
        <button
          type="button"
          className="legal-logo"
          onClick={() => navigate("/")}
          aria-label="VORI 홈으로"
        >
          VORI
        </button>
        <button
          type="button"
          className="legal-back"
          onClick={() => navigate(-1)}
        >
          ← 뒤로
        </button>
      </header>

      <article className="legal-article">
        <h1 className="legal-title">{title}</h1>
        <p className="legal-effective">시행일: {effectiveDate}</p>
        <p className="legal-draft-note">
          본 문서는 졸업작품(VORI) 기준으로 작성된 초안이며, 실서비스 배포 시
          사업자 정보 기재와 전문가 검토가 필요합니다.
        </p>
        {children}
      </article>

      <footer className="legal-footer">졸업작품 © 2026 VORI Team</footer>
    </div>
  );
}

export function Section({ title, children }) {
  return (
    <section className="legal-section">
      <h2 className="legal-section-title">{title}</h2>
      {children}
    </section>
  );
}

export function P({ children }) {
  return <p className="legal-p">{children}</p>;
}

export function Ul({ items }) {
  return (
    <ul className="legal-ul">
      {items.map((it, i) => (
        <li key={i}>{it}</li>
      ))}
    </ul>
  );
}
