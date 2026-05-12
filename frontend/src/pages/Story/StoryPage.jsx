import { useRef, useState } from "react";
import { motion, useScroll, useTransform } from "framer-motion";
import SiteHeader from "../../components/SiteHeader";
// SiteHeader 가 .landing-header 등 랜딩 페이지의 헤더 클래스를 그대로 쓰기
// 때문에, 이 페이지에서도 LandingPage.css 를 함께 import 합니다.
import "../Landing/LandingPage.css";
import "./StoryPage.css";

// 캐릭터 데이터 (placeholder).
// 확정되면 emoji 자리에 <img src="/images/pets/xxx.png" /> 로 교체하고,
// name·tag·desc 만 수정하면 됩니다. 새 캐릭터를 추가할 땐 같은 형식으로
// 배열에 한 줄 추가하세요.
const CHARACTERS = [
  {
    id: "energy-1",
    stat: "energy",
    emoji: "🐮",
    name: "캐릭터 #1",
    tag: "🍖 에너지 · 식비",
    desc: "한 줄 소개가 들어갈 자리예요. 펫의 성격·말투를 짧게.",
    thumb: "energy",
  },
  {
    id: "charm-1",
    stat: "charm",
    emoji: "🐱",
    name: "캐릭터 #2",
    tag: "✨ 매력 · 쇼핑·뷰티",
    desc: "한 줄 소개가 들어갈 자리예요. 어떤 스타일을 좋아하는지.",
    thumb: "charm",
  },
  {
    id: "smart-1",
    stat: "smart",
    emoji: "🦉",
    name: "캐릭터 #3",
    tag: "🧠 지능 · 문화·여가",
    desc: "한 줄 소개가 들어갈 자리예요. 무엇을 가장 좋아하는지.",
    thumb: "smart",
  },
  {
    id: "endure-1",
    stat: "endure",
    emoji: "🐶",
    name: "캐릭터 #4",
    tag: "💪 지구력 · 생활·고정비",
    desc: "한 줄 소개가 들어갈 자리예요. 어떻게 곁에 머무는지.",
    thumb: "endure",
  },
];

// 아이템 데이터 (placeholder). 마이룸 가구·소품 같은 항목이 들어갑니다.
// 추가/수정 시 같은 형식으로 한 줄 추가.
const ITEMS = [
  {
    id: "item-1",
    emoji: "🛏️",
    name: "아이템 #1",
    tag: "마이룸 · 가구",
    desc: "한 줄 소개가 들어갈 자리예요. 어떤 효과를 주는지.",
    thumb: "energy",
  },
  {
    id: "item-2",
    emoji: "🪴",
    name: "아이템 #2",
    tag: "마이룸 · 소품",
    desc: "한 줄 소개가 들어갈 자리예요. 분위기·보너스 등.",
    thumb: "smart",
  },
  {
    id: "item-3",
    emoji: "🎀",
    name: "아이템 #3",
    tag: "마이룸 · 장식",
    desc: "한 줄 소개가 들어갈 자리예요.",
    thumb: "charm",
  },
  {
    id: "item-4",
    emoji: "🎨",
    name: "아이템 #4",
    tag: "마이룸 · 장식",
    desc: "한 줄 소개가 들어갈 자리예요.",
    thumb: "endure",
  },
];

const STORY_TABS = [
  { id: "characters", label: "캐릭터 소개" },
  { id: "items", label: "아이템" },
];

// 애플 AirPods Pro 페이지 스타일 — 단락의 등장 기준이 viewport 세로 중앙.
//   - element top 이 viewport 정중앙(center)에 도달했을 때 또렷해짐
//   - element 가 viewport 위로 흘러나가는 동안 다시 흐려짐
function StoryParagraph({ children }) {
  const ref = useRef(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start 80%", "end start"],
  });
  // 등장 구간을 더 길게(0 → 0.35) 잡아서 opacity 가 점진적으로 올라오면서
  // y 도 같은 속도로 부드럽게 움직이게 합니다.
  const opacity = useTransform(
    scrollYProgress,
    [0, 0.15, 0.35, 0.7, 1],
    [0, 0.4, 1, 1, 0]
  );
  const y = useTransform(scrollYProgress, [0, 0.35], [60, 0]);
  return (
    <motion.p ref={ref} className="story-paragraph" style={{ opacity, y }}>
      {children}
    </motion.p>
  );
}

// 스토리 페이지.
// 흐름은 "우주(가장 어두움) → 별로 다가감 → 도착(베이지)" 순으로 점진적으로
// 밝아져요. 구간별로 .story-section--{단계} 클래스를 두어 배경/글자 색을
// 조절합니다.
//
// 챕터는 개수 제한이 없어요. 기존 패턴을 따라
//   <section className="story-section story-section--{dark|twilight|light}">
//     <article className="story-chapter"> ... </article>
//   </section>
// 형태로 자유롭게 추가/제거하시면 됩니다.
//
// 스토리 챕터 이후엔 "펫 안내" 섹션이 이어집니다. 콘텐츠를 손볼 땐 각
// <article> 또는 .story-pets-block 안의 텍스트만 수정하세요.
function StoryPage({ onNavigate }) {
  const [activeTab, setActiveTab] = useState("characters");
  const ch3ArticleRef = useRef(null);

  // 페이지 전체 스크롤 진행도(0~1)에 비례해 달이 자전합니다.
  const { scrollYProgress } = useScroll();
  const moonRotate = useTransform(scrollYProgress, [0, 1], [0, 720]);

  // chapter 3 의 article(본문 영역) 자체를 ref 로 추적해서,
  // 본문이 viewport 가운데에 있을 때 달도 가운데, 본문이 위로 흐르는 만큼
  // 달도 함께 위로 흘러가도록 합니다.
  const { scrollYProgress: ch3Progress } = useScroll({
    target: ch3ArticleRef,
    offset: ["start end", "end start"],
  });
  const moonTop = useTransform(
    ch3Progress,
    [0, 0.58, 1],
    ["42vh", "42vh", "-20vh"]
  );

  const goLanding = () => {
    if (typeof onNavigate === "function") onNavigate("landing");
  };

  return (
    <div className="story">
      {/* ───────── 헤더 (랜딩과 같은 디자인이되 배경 투명 + 메뉴 숨김) ───────── */}
      <SiteHeader onNavigate={onNavigate} transparent minimal />

      {/* ───────── ① 표지 (우주, 가장 어두움) ───────── */}
      <section className="story-cover">
        <div className="story-stars" aria-hidden />
        <div className="story-cover-inner">
          <span className="story-eyebrow">VORI 의 작은 세계</span>
          <h1 className="story-cover-title">
            달나라에서 온 작은 손님,<br />
            그리고 깨어나길 기다리는 친구들
          </h1>
          <p className="story-cover-sub">
            외계 토끼가 가져온 부탁
          </p>
          <div className="story-cover-hint" aria-hidden>
            ↓ 스크롤
          </div>
        </div>
      </section>

      {/* ───────── 챕터 영역 (달은 sticky + chapter 3 진행에 맞춰 위로 흐름) ───────── */}
      <div className="story-chapters">
        <motion.div
          className="story-moon"
          style={{ top: moonTop }}
          aria-hidden
        >
          <motion.div
            className="story-moon-body"
            style={{ rotate: moonRotate }}
          />
        </motion.div>

      {/* ───────── ② Chapter 01 (짙은 남청) ───────── */}
      <section className="story-section story-section--dark">
        <article className="story-chapter">
          <div className="story-chapter-head">
            <div className="story-chapter-mark">Chapter 01</div>
            <h2 className="story-chapter-title">달나라에서 온 부탁</h2>
          </div>
          <StoryParagraph>
            어느 밤, 작은 <em>외계 토끼</em>가 달에서 내려와 당신의 화면을
            두드립니다. 토끼는 조심스럽게 부탁을 건네요 —
            “우리 친구들을 구해 주세요. 그 아이들은 <strong>떡</strong>이
            부족해서 좀처럼 깨어나지 못해요.”
          </StoryParagraph>
          <StoryParagraph>
            달나라엔 떡이 자라지 않습니다. 떡은 오직 누군가의
            <em> 합리적인 마음</em>에서 빚어지기 때문이에요. 당신이 매일의
            작은 선택을 기록하면, 그 마음이 떡이 되어 멀리 달나라로 보내집니다.
            펫들이 깨어날 시간이 다가오는 거예요.
          </StoryParagraph>
        </article>
      </section>

      {/* ───────── ③ Chapter 02 (트와일라잇, 점점 밝아짐) ───────── */}
      <section className="story-section story-section--twilight">
        <article className="story-chapter">
          <div className="story-chapter-head">
            <div className="story-chapter-mark">Chapter 02</div>
            <h2 className="story-chapter-title">떡이 자라는 시간</h2>
          </div>
          <StoryParagraph>
            당신이 합리적인 소비를 할 때마다, 멀리 달나라의 펫들 곁에 작은
            떡이 하나 놓입니다. 떡을 먹은 펫들은 잠에서 깨어나 스탯을 한 칸씩
            채워가요 —
            <strong> 🍖 에너지 · ✨ 매력 · 🧠 지능 · 💪 지구력</strong>.
          </StoryParagraph>
          <StoryParagraph>
            너무 많이 쓴 날엔 펫이 가만히 다가와 귀띔합니다. “오늘은 떡이
            조금 부족했어요.” 매일 밤 외계 토끼가 그날의 지출을 함께
            훑어보고, 이례적인 한 가지에 대해서는 부드럽게 사유를 묻습니다.
          </StoryParagraph>
          <StoryParagraph>
            그렇게 떡을 나누며 보낸 시간이 쌓이면, 어린 펫이 청소년기를 지나
            성체로, 그리고 그 너머의 모습으로 자랍니다. 펫의 진화는 결국,
            <em> 당신이 지난 시간을 어떻게 살아왔는지의 기록</em> 이에요.
          </StoryParagraph>
        </article>
      </section>

      {/* ───────── ④ Chapter 03 (도착, 베이지) ───────── */}
      <section className="story-section story-section--light">
        {/* article 자체를 ref 로 추적 → 본문 영역과 정확히 동기화 */}
        <article ref={ch3ArticleRef} className="story-chapter">
          <div className="story-chapter-head">
            <div className="story-chapter-mark">Chapter 03</div>
            <h2 className="story-chapter-title">깨어난 친구들</h2>
          </div>
          <StoryParagraph>
            당신의 떡을 받은 친구들이 하나둘 눈을 뜨기 시작했어요. 외계
            토끼는 그 곁에서 살며시 미소 짓고, 깨어난 친구는 가만히 당신을
            바라보다 작은 머리를 끄덕입니다. <em>부탁은 그렇게 조금씩
            이루어져요.</em>
          </StoryParagraph>
          <StoryParagraph>
            성체가 된 친구는 다시 자기의 별을 찾아 떠나고, 그 자리엔 또
            다른 잠든 친구가 도착합니다. 마이룸엔 그 동안의 흔적이 차곡차곡
            쌓이고요. 그렇게 당신의 별은
            <strong> 깨어나는 친구들의 정거장</strong>이 됩니다.
          </StoryParagraph>
        </article>
      </section>

      </div>{/* /.story-chapters */}

      {/* ───────── 펫 안내 (메이플 직업 페이지 스타일 그리드) ──────── */}
      {/* 캐릭터는 placeholder 4종(스탯 4종 대응). 캐릭터 확정 시 emoji /
          이름 / tag / desc 만 수정하면 됩니다. 이미지가 준비되면 thumb 의
          이모지를 <img src=... /> 로 교체. */}
      <section className="story-section story-section--light story-section--pets">
        <div className="story-pets">
          <div className="story-pets-head">
            <span className="story-eyebrow story-eyebrow--dark">달나라 안내서</span>
            <h2 className="story-pets-title">달나라의 친구들</h2>
            <p className="story-pets-sub">
              잠든 친구들이 어떤 모습으로 깨어나는지. 곧 당신의 떡이 그들의
              하루를 시작하게 할 거예요.
            </p>
          </div>

          {/* 탭: 캐릭터 소개 / 아이템 */}
          <div className="story-tabs" role="tablist">
            {STORY_TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.id}
                className={`story-tab ${
                  activeTab === tab.id ? "story-tab--active" : ""
                }`}
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* 활성 탭에 따라 카드 그리드 분기 */}
          <div className="story-character-grid" key={activeTab}>
            {(activeTab === "characters" ? CHARACTERS : ITEMS).map((c) => (
              <article key={c.id} className="story-character">
                <div
                  className={`story-character-thumb story-character-thumb--${c.thumb}`}
                >
                  <span className="story-character-emoji" aria-hidden>
                    {c.emoji}
                  </span>
                </div>
                <div className="story-character-info">
                  <div className="story-character-tag">{c.tag}</div>
                  <h3 className="story-character-name">{c.name}</h3>
                  <p className="story-character-desc">{c.desc}</p>
                </div>
              </article>
            ))}
          </div>

          {/* 보조 정보 — 성장·업적·분양 (짧게) */}
          <div className="story-info-row">
            <div className="story-info">
              <div className="story-info-icon" aria-hidden>🌱</div>
              <div className="story-info-title">성장 단계</div>
              <div className="story-info-desc">
                유년기 → 청소년기 → 성체. 스탯의 비율에 따라 같은 펫도
                다른 모습으로 자라요.
              </div>
            </div>
            <div className="story-info">
              <div className="story-info-icon" aria-hidden>🏆</div>
              <div className="story-info-title">업적과 칭호</div>
              <div className="story-info-desc">
                조건을 채우면 새로운 칭호가 열려요. 키우는 공간의 배경도
                함께 바뀝니다.
              </div>
            </div>
            <div className="story-info">
              <div className="story-info-icon" aria-hidden>🎁</div>
              <div className="story-info-title">분양과 마이룸</div>
              <div className="story-info-desc">
                성체가 된 펫은 다른 곳으로 분양 가능. 모인 게임 머니로
                마이룸을 꾸미면 다음 펫에게 보너스가 돌아와요.
              </div>
            </div>
          </div>

          <div className="story-back">
            <button
              type="button"
              className="story-back-btn"
              onClick={goLanding}
            >
              메인으로 돌아가기
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

export default StoryPage;
