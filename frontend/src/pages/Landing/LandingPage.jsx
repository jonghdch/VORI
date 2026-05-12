import { useState } from "react";
import SiteHeader from "../../components/SiteHeader";
import "./LandingPage.css";

// FAQ 한 항목 단위 컴포넌트.
// 클릭하면 부드럽게 펼쳐지고 닫혀요. (grid-template-rows 트릭 사용)
function FaqItem({ question, children }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={`landing-faq-item ${open ? "is-open" : ""}`}>
      <button
        type="button"
        className="landing-faq-q"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <span>{question}</span>
        <span className="landing-faq-icon" aria-hidden>＋</span>
      </button>
      <div className="landing-faq-a-wrap">
        <div className="landing-faq-a-inner">
          <p className="landing-faq-a">{children}</p>
        </div>
      </div>
    </div>
  );
}

// 로그인 전 메인(랜딩) 페이지.
// 페이지 단위로 폴더를 잡아뒀어요: src/pages/<페이지이름>/<페이지이름>.jsx
// 이 파일 하나만 수정하면 이 페이지의 내용이 바뀝니다.
//
// onNavigate(pageName) 을 호출하면 다른 페이지로 이동합니다.
// 예) onNavigate("login") → 로그인 페이지로 이동

function LandingPage({ onNavigate }) {
  const goSignup = () => {
    if (typeof onNavigate === "function") onNavigate("signup");
  };
  const goStory = () => {
    if (typeof onNavigate === "function") onNavigate("story");
  };
  return (
    <div className="landing">
      <SiteHeader onNavigate={onNavigate} />

      {/* ───────── 히어로 (큰 타이틀 영역) ───────── */}
      <section className="landing-hero">
        {/* 배경 이미지(가로로 눕혀서 사용). public/images/hero-bg.jpg 를 바꾸면 됩니다. */}
        <img
          className="landing-hero-bg"
          src={`${process.env.PUBLIC_URL}/images/hero-bg.jpg`}
          alt=""
          aria-hidden
        />
        <div className="landing-hero-inner">
          <div className="landing-hero-text">
            <h1 className="landing-title">
              펫과 함께 자라는<br />
              <span className="landing-title-accent">똑똑한 소비 습관</span>
            </h1>
            <p className="landing-subtitle">
              지출에 사유를 적으면, AI가 합리적인 소비였는지 알려줘요.<br />
              현명하게 쓸수록 펫이 무럭무럭 자랍니다.
            </p>
            <div className="landing-cta-row">
              <button
                className="landing-btn landing-btn-primary landing-btn-lg"
                type="button"
                onClick={goSignup}
              >
                지금 펫과 함께 시작하기
              </button>
              <button
                className="landing-btn landing-btn-ghost landing-btn-lg"
                type="button"
                onClick={goStory}
              >
                스토리 보기
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* ───────── 우리 이야기 (간단 한 단락) ───────── */}
      <section className="landing-story">
        <div className="landing-story-inner">
          <span className="landing-story-eyebrow">우리 이야기</span>
          <h2 className="landing-story-title">
            지출이 부담이 아닌,<br />
            펫과의 작은 일과가 되도록
          </h2>
          <p className="landing-story-body">
            가계부는 결과가 잘 보이지 않아서 금세 지칩니다. VORI는 매일의 작은
            선택이 펫의 성장으로 이어지도록 만들었어요. 합리적인 소비는
            기록 그 자체로 보상이 되고, 그렇지 않은 날에는 펫이 가볍게
            귀띔해줍니다. 가계부의 부담을 덜고, 키우는 재미로 이어가는 것 —
            그게 VORI 가 지향하는 모습입니다.
          </p>
          <p className="landing-story-meta">졸업작품 · VORI Team</p>
        </div>
      </section>

      {/* ───────── 핵심 기능 카드 ───────── */}
      <section id="features" className="landing-features">
        <div className="landing-section-head">
          <h2 className="landing-section-title">가계부, 그 이상의 경험</h2>
          <p className="landing-section-sub">
            가계부, 펫 육성, AI 코칭이 하나로 연결돼 있어요.
          </p>
        </div>
        <div className="landing-feature-grid">
          <article className="landing-feature-card">
            <div className="landing-feature-head">
              <div className="landing-feature-icon landing-icon-green">💰</div>
              <h3 className="landing-feature-title">사유 가계부</h3>
            </div>
            <p className="landing-feature-desc">
              지출 금액과 함께 "왜 썼는지"를 남겨요.
              나중에 다시 봤을 때 패턴이 보입니다.
            </p>
          </article>
          <article className="landing-feature-card">
            <div className="landing-feature-head">
              <div className="landing-feature-icon landing-icon-purple">🏷️</div>
              <h3 className="landing-feature-title">카테고리 자동 분류</h3>
            </div>
            <p className="landing-feature-desc">
              지출 내용을 적기만 하면 AI가 식비·문화·고정비 등으로
              알아서 분류해줘요. 매번 카테고리를 고를 필요가 없습니다.
            </p>
          </article>
          <article className="landing-feature-card">
            <div className="landing-feature-head">
              <div className="landing-feature-icon landing-icon-orange">🤖</div>
              <h3 className="landing-feature-title">AI 합리성 판정</h3>
            </div>
            <p className="landing-feature-desc">
              4가지 알고리즘이 지출을 분석해
              🟢 합리적 / ⚪ 중립 / 🔴 과소비 시그널을 줍니다.
            </p>
          </article>
          <article className="landing-feature-card">
            <div className="landing-feature-head">
              <div className="landing-feature-icon landing-icon-yellow">🌱</div>
              <h3 className="landing-feature-title">펫 키우기</h3>
            </div>
            <p className="landing-feature-desc">
              합리적인 소비를 할수록 펫의 스탯이 오르고
              새로운 성장 단계로 진화해요.
            </p>
          </article>
          <article className="landing-feature-card">
            <div className="landing-feature-head">
              <div className="landing-feature-icon landing-icon-blue">🏠</div>
              <h3 className="landing-feature-title">마이룸 & 업적</h3>
            </div>
            <p className="landing-feature-desc">
              아낀 만큼 상점에서 아이템을 사고
              나만의 방을 꾸미며 업적을 모아보세요.
            </p>
          </article>
        </div>
      </section>

      {/* ───────── 이용 방법 3단계 ───────── */}
      <section id="how" className="landing-how">
        <div className="landing-section-head">
          <h2 className="landing-section-title">이렇게 사용해요</h2>
          <p className="landing-section-sub">
            세 단계면 펫과 함께하는 소비 일기가 시작돼요.
          </p>
        </div>
        <div className="landing-step-row">
          <div className="landing-step">
            <div className="landing-step-text">
              <div className="landing-step-head">
                <div className="landing-step-num">01</div>
                <h3 className="landing-step-title">지출 + 사유 입력</h3>
              </div>
              <p className="landing-step-desc">
                오늘 쓴 돈과 이유를 한 줄로 적어요.<br />
                "점심에 친구랑 파스타" 처럼 자유롭게.
              </p>
            </div>
            <div className="landing-step-media">
              {/* public/videos/step1.mp4 파일을 넣으면 자동 재생됩니다. */}
              <video
                className="landing-step-video"
                src={`${process.env.PUBLIC_URL}/videos/step1.mp4`}
                autoPlay
                loop
                muted
                playsInline
              />
            </div>
          </div>

          <div className="landing-step">
            <div className="landing-step-text">
              <div className="landing-step-head">
                <div className="landing-step-num">02</div>
                <h3 className="landing-step-title">AI가 시그널 판정</h3>
              </div>
              <p className="landing-step-desc">
                과거 소비, 예산, 패턴을 함께 보고
                합리성을 판단해 알려드려요.
              </p>
            </div>
            <div className="landing-step-media">
              {/* public/videos/step2.mp4 파일을 넣으면 자동 재생됩니다. */}
              <video
                className="landing-step-video"
                src={`${process.env.PUBLIC_URL}/videos/step2.mp4`}
                autoPlay
                loop
                muted
                playsInline
              />
            </div>
          </div>

          <div className="landing-step">
            <div className="landing-step-text">
              <div className="landing-step-head">
                <div className="landing-step-num">03</div>
                <h3 className="landing-step-title">펫이 성장</h3>
              </div>
              <p className="landing-step-desc">
                에너지·매력·지능·지구력 스탯이 오르고,
                마이룸에서 결과를 확인해요.
              </p>
            </div>
            <div className="landing-step-media">
              {/* public/videos/step3.mp4 파일을 넣으면 자동 재생됩니다. */}
              <video
                className="landing-step-video"
                src={`${process.env.PUBLIC_URL}/videos/step3.mp4`}
                autoPlay
                loop
                muted
                playsInline
              />
            </div>
          </div>
        </div>
      </section>

      {/* ───────── FAQ ───────── */}
      <section className="landing-faq">
        <div className="landing-section-head">
          <h2 className="landing-section-title">자주 묻는 질문</h2>
          <p className="landing-section-sub">
            궁금한 것들을 모아뒀어요. 더 있으면 언제든 문의해 주세요.
          </p>
        </div>
        <div className="landing-faq-list">
          <FaqItem question="VORI 는 무료인가요?">
            네, 가입과 기본 기능은 모두 무료예요. 졸업작품 단계라 별도의
            결제 절차 없이 모든 기능을 사용해보실 수 있습니다.
          </FaqItem>
          <FaqItem question="내 지출 데이터는 안전한가요?">
            지출 내역은 본인 계정에만 연결되며, 분석 외 다른 목적으로는
            사용하지 않아요. 비밀번호는 암호화하여 저장하고, 통신 구간도
            모두 HTTPS 로 보호됩니다.
          </FaqItem>
          <FaqItem question="AI 는 어떤 기준으로 합리성을 판정하나요?">
            사용자의 예산, 과거 소비 패턴, 카테고리별 평균, 그리고 입력한
            사유 텍스트 네 가지를 함께 봅니다. 단일 알고리즘이 아니라 네
            가지 관점이 교차 검증된 결과를 시그널로 보여드려요.
          </FaqItem>
          <FaqItem question="PC 와 모바일 모두 되나요?">
            네, 웹 브라우저 기반이라 PC, 태블릿, 모바일 어디서든 동일한
            계정으로 사용하실 수 있습니다.
          </FaqItem>
        </div>
      </section>

      {/* ───────── 하단 CTA ───────── */}
      <section className="landing-bottom-cta">
        <div className="landing-bottom-cta-inner">
          <h2 className="landing-bottom-cta-title">
            오늘부터 펫과 함께해볼까요?
          </h2>
          <p className="landing-bottom-cta-sub">
            가입은 30초, 첫 지출 기록부터 펫이 반응합니다.
          </p>
          <button
            className="landing-btn landing-btn-primary landing-btn-lg"
            type="button"
            onClick={goSignup}
          >
            시작하기
          </button>
        </div>
      </section>


      {/* ───────── 푸터 ───────── */}
      <footer id="team" className="landing-footer">
        <div className="landing-footer-inner">
          <div className="landing-footer-brand">
            <div className="landing-logo landing-logo-sm">VORI</div>
            <p className="landing-footer-desc">
              AI 스토리텔링 가계부 · 펫 육성 서비스
            </p>
          </div>
          <div className="landing-footer-meta">
            <span>졸업작품 © 2026 VORI Team</span>
            <span>문의 : vori@example.com</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default LandingPage;
