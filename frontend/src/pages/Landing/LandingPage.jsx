import { useState } from "react";
import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";
import SiteHeader from "../../components/SiteHeader";
import boriImage from "../../assets/pets/bori.png";
import "./LandingPage.css";

function MockWindow({ label, children }) {
  return <div className="landing-mock"><div className="landing-mock-bar"><span className="landing-mock-dot landing-mock-dot--r" /><span className="landing-mock-dot landing-mock-dot--y" /><span className="landing-mock-dot landing-mock-dot--g" /><span className="landing-mock-bar-title">{label}</span></div><div className="landing-mock-body">{children}</div></div>;
}

function MockEntry() {
  return <MockWindow label="오늘 지출 · 새 기록"><div className="landing-mock-row"><span className="landing-mock-label">내역</span><span className="landing-mock-input">친구와 저녁 파스타</span></div><div className="landing-mock-row"><span className="landing-mock-label">금액</span><span className="landing-mock-input landing-mock-amount">18,000원</span></div><div className="landing-mock-row"><span className="landing-mock-label">분류</span><span className="landing-mock-chip">식비 · 외식</span></div><div className="landing-mock-reason"><span className="landing-mock-label">메모</span><p>오랜만에 만난 친구와 함께한 저녁</p></div></MockWindow>;
}

function MockVerdict() {
  return <MockWindow label="오늘의 소비 판정"><div className="landing-mock-verdict"><span className="landing-mock-signals" aria-hidden><span className="landing-mock-sig landing-mock-sig--on" /><span className="landing-mock-sig landing-mock-sig--gray" /><span className="landing-mock-sig landing-mock-sig--red" /></span><strong className="landing-mock-verdict-text">합리적인 소비</strong></div><ul className="landing-mock-reasons"><li>평소 외식 범위와 비슷해요</li><li>소비한 이유가 분명해요</li><li>이번 달 흐름에서 무리가 없어요</li></ul></MockWindow>;
}

function MockGrowth() {
  const stats = [{ label: "에너지", pct: 72 }, { label: "매력", pct: 58 }, { label: "지능", pct: 66 }, { label: "지구력", pct: 80 }];
  return <MockWindow label="보리의 방"><div className="landing-mock-pet"><span className="landing-mock-pet-avatar"><img src={boriImage} alt="" /></span><span className="landing-mock-pet-meta"><span className="landing-mock-pet-name">보리</span><span className="landing-mock-pet-sub">오늘도 함께 성장했어요</span></span><span className="landing-mock-grow">스탯 +2</span></div><ul className="landing-mock-stats">{stats.map((stat) => <li key={stat.label}><span>{stat.label}</span><span className="landing-mock-track"><span className="landing-mock-fill" style={{ width: `${stat.pct}%` }} /></span><span className="landing-mock-val">{stat.pct}</span></li>)}</ul></MockWindow>;
}

function HeroPreview() {
  return <div className="landing-studio-preview"><div className="landing-studio-preview-head"><span>오늘의 소비</span><span className="landing-studio-live"><i /> 분석 완료</span></div><div className="landing-studio-preview-body"><div className="landing-studio-score"><span className="landing-studio-score-ring">A</span><div><strong>좋은 흐름이에요</strong><p>내 기준에 맞게 소비했어요</p></div></div><div className="landing-studio-preview-row"><span>친구와 저녁</span><strong>18,000원</strong><em>합리적</em></div><div className="landing-studio-preview-row"><span>교통카드 충전</span><strong>30,000원</strong><em>합리적</em></div><div className="landing-studio-pet-card"><img src={boriImage} alt="보리" /><div><small>오늘의 성장</small><strong>에너지 +2</strong></div><span>Lv. 3</span></div></div></div>;
}

function FaqItem({ question, children }) {
  const [open, setOpen] = useState(false);
  return <div className={`landing-faq-item ${open ? "is-open" : ""}`}><button type="button" className="landing-faq-q" onClick={() => setOpen((value) => !value)} aria-expanded={open}><span>{question}</span><span className="landing-faq-icon" aria-hidden>＋</span></button><div className="landing-faq-a-wrap"><div className="landing-faq-a-inner"><p className="landing-faq-a">{children}</p></div></div></div>;
}

function LandingStepCard({ number, title, desc, mock }) {
  return <motion.article className="landing-step" initial={{ opacity: 0, y: 36 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.25 }} transition={{ duration: 0.65, ease: [0.22, 1, 0.36, 1] }}><div className="landing-step-text"><div className="landing-step-head"><div className="landing-step-num">{number}</div><h3 className="landing-step-title">{title}</h3></div><p className="landing-step-desc">{desc}</p></div><div className="landing-step-media">{mock}</div></motion.article>;
}

const FEATURES = [
  { icon: "✎", title: "빠른 소비 기록", desc: "내역과 금액만 적으면 카테고리를 자동으로 분류하고, 영수증 사진도 읽어드려요." },
  { icon: "◎", title: "내 기준의 소비 판정", desc: "남과 비교하지 않고 나의 평소 소비 패턴을 기준으로 초록·회색·빨강 신호를 보여줘요." },
  { icon: "✦", title: "이유를 묻는 AI", desc: "예외적인 지출에는 혼내는 대신 왜 필요했는지 묻고, 답변을 반영해 다시 판단해요." },
  { icon: "↗", title: "눈에 보이는 성장", desc: "아낀 금액은 펫의 네 가지 스탯과 게임머니로 이어져 기록을 계속할 이유가 생겨요." },
  { icon: "▦", title: "주간·월간 리포트", desc: "흩어진 기록을 기간별로 모아 자주 쓰는 곳과 소비 흐름을 한눈에 확인해요." },
  { icon: "⌂", title: "마이룸과 도감", desc: "펫을 키우고 방을 꾸미며 칭호를 모으는 재미가 꾸준한 소비 습관을 만들어줘요." },
];

function LandingPage({ user, onLogout }) {
  const navigate = useNavigate();
  const startPath = user ? "/home" : "/signup";
  return <div className="landing landing-studio"><SiteHeader user={user} onLogout={onLogout} /><main>
    <section className="landing-studio-hero"><img className="landing-studio-hero-bg" src={`${process.env.PUBLIC_URL}/images/hero-bg.jpg`} alt="" aria-hidden /><div className="landing-studio-hero-shade" /><div className="landing-studio-container landing-studio-hero-grid"><motion.div className="landing-studio-hero-copy" initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7 }}><span className="landing-studio-kicker">AI CONSUMPTION JOURNAL</span><h1>쓴 돈을 기록하면,<br /><strong>내 소비가 이해되기 시작해요.</strong></h1><p>VORI는 금액만 세는 가계부가 아니에요. 나의 평소 패턴과 소비 이유를 함께 살피고, 더 나은 선택을 펫의 성장으로 돌려드립니다.</p><div className="landing-studio-tags"><span>#자동분류</span><span>#AI판정</span><span>#펫성장</span><span>#소비리포트</span></div><div className="landing-studio-actions"><button type="button" className="landing-studio-primary" onClick={() => navigate(startPath)}>{user ? "내 기록 보러가기" : "무료로 시작하기"} <span>→</span></button><button type="button" className="landing-studio-secondary" onClick={() => document.getElementById("how")?.scrollIntoView({ behavior: "smooth" })}>사용 방법 보기</button></div></motion.div><motion.div className="landing-studio-hero-visual" initial={{ opacity: 0, x: 28 }} animate={{ opacity: 1, x: 0 }} transition={{ duration: 0.8, delay: 0.12 }}><HeroPreview /></motion.div></div></section>
    <section className="landing-studio-facts" aria-label="VORI 핵심 특징"><div className="landing-studio-container landing-studio-facts-grid"><div><strong>3단계</strong><span>기록부터 성장까지</span></div><div><strong>3가지</strong><span>소비 시그널</span></div><div><strong>4종</strong><span>펫 성장 스탯</span></div><div><strong>16종</strong><span>함께할 펫</span></div></div></section>
    <section id="features" className="landing-studio-services"><div className="landing-studio-container"><div className="landing-studio-section-head"><span>WHAT VORI DOES</span><h2><em>소비 기록</em>이 습관이 되도록</h2><p>입력의 번거로움은 줄이고, 기록 뒤에 돌아오는 가치는 더 분명하게 만들었어요.</p></div><div className="landing-studio-service-grid">{FEATURES.map((feature, index) => <motion.article key={feature.title} className="landing-studio-service-card" initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, amount: 0.25 }} transition={{ duration: 0.5, delay: index * 0.05 }}><span className="landing-studio-service-icon">{feature.icon}</span><h3>{feature.title}</h3><p>{feature.desc}</p><span className="landing-studio-card-index">0{index + 1}</span></motion.article>)}</div></div></section>
    <section className="landing-studio-value"><div className="landing-studio-container landing-studio-value-grid"><div className="landing-studio-value-copy"><span className="landing-studio-kicker">AFTER RECORDING</span><h2>기록으로 끝나지 않고,<br /><strong>다음 선택까지 이어져요.</strong></h2><p>오늘의 소비를 이해하고, 내일의 기준을 만들고, 그 과정이 보리의 성장으로 남습니다.</p><button type="button" onClick={() => navigate("/story")}>VORI 이야기 보기 <span>→</span></button></div><div className="landing-studio-value-list"><article><span>01</span><div><h3>평소의 나와 비교</h3><p>고정된 정답 대신 개인별 소비 평균과 변화 흐름을 사용해요.</p></div></article><article><span>02</span><div><h3>맥락까지 다시 판단</h3><p>경조사, 긴급 지출, 자기투자처럼 금액만으로 알 수 없는 이유를 반영해요.</p></div></article><article><span>03</span><div><h3>성장으로 돌아오는 보상</h3><p>합리적인 선택과 절약이 펫 스탯, 게임머니, 업적으로 연결돼요.</p></div></article></div></div></section>
    <section id="how" className="landing-how landing-studio-how"><div className="landing-studio-section-head"><span>HOW IT WORKS</span><h2>딱 세 단계면 충분해요</h2><p>복잡한 설정 없이 오늘 쓴 돈부터 가볍게 시작하세요.</p></div><div className="landing-step-row"><LandingStepCard number="01" title="오늘의 소비를 기록해요" desc="내역과 금액을 적으면 VORI가 카테고리를 자동으로 찾아요." mock={<MockEntry />} /><LandingStepCard number="02" title="내 기준으로 살펴봐요" desc="평소 패턴에서 벗어난 지출은 이유까지 듣고 시그널을 정해요." mock={<MockVerdict />} /><LandingStepCard number="03" title="좋은 선택이 성장해요" desc="절약한 만큼 펫의 스탯이 오르고 새로운 성장 단계가 열려요." mock={<MockGrowth />} /></div></section>
    <section className="landing-faq landing-studio-faq"><div className="landing-studio-section-head"><span>FAQ</span><h2>시작하기 전에 궁금한 점</h2></div><div className="landing-faq-list"><FaqItem question="VORI는 무료인가요?">네. 현재 가입과 제공되는 기본 기능은 별도 결제 없이 사용할 수 있어요.</FaqItem><FaqItem question="AI는 어떤 기준으로 판단하나요?">사용자별 카테고리 소비 평균과 변화량을 먼저 계산하고, 예외적인 지출에는 사용자가 적은 이유를 추가로 반영해요.</FaqItem><FaqItem question="처음 가입하면 기록이 없어도 괜찮나요?">소비 프로필로 초기 기준을 부드럽게 잡고, 실제 기록이 쌓일수록 나에게 맞는 기준으로 조정돼요.</FaqItem><FaqItem question="모바일에서도 사용할 수 있나요?">네. 별도 앱 설치 없이 모바일과 PC 웹 브라우저에서 사용할 수 있어요.</FaqItem></div></section>
    <section className="landing-studio-final"><div className="landing-studio-container"><span>READY TO START?</span><h2>오늘 쓴 돈 하나부터,<br />보리와 함께 기록해보세요.</h2><p>몇 분이면 내 소비 기준이 준비됩니다.</p><button type="button" onClick={() => navigate(startPath)}>{user ? "VORI로 돌아가기" : "무료로 시작하기"} <span>→</span></button></div></section>
  </main><footer id="team" className="landing-footer"><div className="landing-footer-inner"><div className="landing-footer-brand"><div className="landing-logo landing-logo-sm">VORI</div><p className="landing-footer-desc">AI 소비 기록 · 펫 성장 서비스</p></div><div className="landing-footer-meta"><div className="landing-footer-legal"><a href="/terms">이용약관</a><span aria-hidden>·</span><a href="/privacy">개인정보처리방침</a></div><span>졸업작품 © 2026 VORI Team</span></div></div></footer></div>;
}

export default LandingPage;
