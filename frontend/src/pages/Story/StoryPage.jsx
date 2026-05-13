import { useEffect, useRef, useState } from "react";
import { motion, useScroll, useTransform } from "framer-motion";
import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import SiteHeader from "../../components/SiteHeader";
// SiteHeader 가 .landing-header 등 랜딩 페이지의 헤더 클래스를 그대로 쓰기
// 때문에, 이 페이지에서도 LandingPage.css 를 함께 import 합니다.
import "../Landing/LandingPage.css";
import "./StoryPage.css";

// 캐릭터 데이터 (placeholder).
// 확정되면 emoji 자리에 <img src="/images/pets/xxx.png" /> 로 교체하고,
// name·desc 만 수정하면 됩니다.
const CHARACTERS = [
  { id: "dragon", emoji: "🐲", name: "용",     desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "lion",   emoji: "🦁", name: "사자",   desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "snake",  emoji: "🐍", name: "뱀",     desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "fox",    emoji: "🦊", name: "여우",   desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "deer",   emoji: "🦌", name: "사슴",   desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "penguin",emoji: "🐧", name: "펭귄",   desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "wolf",   emoji: "🐺", name: "늑대",   desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "turtle", emoji: "🐢", name: "거북이", desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "dog",    emoji: "🐶", name: "강아지", desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "cat",    emoji: "🐱", name: "고양이", desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "rabbit", emoji: "🐰", name: "토끼",   desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "sheep",  emoji: "🐑", name: "양",     desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "frog",   emoji: "🐸", name: "개구리", desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "squirrel",emoji: "🐿️", name: "다람쥐", desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "monkey", emoji: "🐵", name: "원숭이", desc: "한 줄 소개가 들어갈 자리예요." },
  { id: "panda",  emoji: "🐼", name: "팬더",   desc: "한 줄 소개가 들어갈 자리예요." },
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

const EVOLUTION_STAGES = ["유년기", "청소년기", "성체"];

const MOON_TOP_INPUT = [0, 0.58, 1];
const MOON_TOP_OUTPUT = ["50vh", "50vh", "-20vh"];
const MOON_MODEL_PATH = `${process.env.PUBLIC_URL}/models/moon.glb`;

const STORY_CHAPTERS = [
  {
    id: "chapter-1",
    sectionClass: "story-section--dark",
    mark: "Chapter 01",
    title: "태양계의 오래된 질서",
    paragraphs: [
      <>
        태양계의 중심에는 <em>태양</em>이 있습니다. 태양은 누구의 편도
        들지 않는 중립의 축. 모든 별은 그 빛 아래에서 각자의 궤도를 돌고,
        오래된 질서는 그렇게 유지되어 왔습니다.
      </>,
      <>
        하지만 화성은 달랐습니다. 태양의 곁을 맴도는 작은 권력자처럼
        사사건건 목소리를 높였고, 지구에게는 감히 맞서지 못한 채
        <strong> 달을 향해 방해 파장</strong>을 쏘아 보내기 시작했어요.
      </>,
    ],
  },
  {
    id: "chapter-2",
    sectionClass: "story-section--twilight",
    mark: "Chapter 02",
    title: "루미나의 잠든 친구들",
    paragraphs: [
      <>
        달나라에는 <em>루미나</em>라는 작은 나라가 있습니다. 루미나의
        친구들은 지구 사람들이 달을 보며 비는 소원을 모아 살아가요.
        누군가의 소원이 이루어질수록, 루미나의 행복도 함께 차오릅니다.
      </>,
      <>
        그러나 화성의 파장이 달을 흔들기 시작하면서, 소원은 길을 잃고
        친구들은 지쳐갔습니다. 과도한 스트레스를 버티지 못한 이들은 하나둘
        <strong> 단단한 알</strong>이 되어 잠들었고, 루미나를 지킬 힘은
        눈에 띄게 약해졌습니다.
      </>,
      <>
        남은 친구들은 알을 지키기에도 벅찼고, 결국 루미나는 피난을
        결정합니다. 목적지는 목성 주변을 도는 위성, <em>가니메데</em>.
        그곳이라면 화성의 파장에서 잠시 벗어날 수 있을 거라 믿었어요.
      </>,
    ],
  },
  {
    id: "chapter-3",
    sectionClass: "story-section--light",
    mark: "Chapter 03",
    title: "토끼가 들은 가장 강한 소원",
    trackMoonExit: true,
    paragraphs: [
      <>
        튜토리얼 토끼는 잠든 알들을 품에 안고 가니메데를 향해 떠났습니다.
        달의 궤도를 벗어나던 바로 그때, 토끼는 지구 쪽에서 밀려오는
        이상할 만큼 또렷한 소원을 들었어요.
      </>,
      <>
        그 소원은 단순히 무언가를 갖고 싶다는 말이 아니었습니다. 더 나은
        하루를 만들고 싶다는 마음, 스스로의 선택을 이해하고 싶다는 마음.
        토끼는 방향을 돌려 당신에게 다가옵니다. 이제 당신의 기록은
        <strong> 잠든 루미나 친구들을 깨우는 힘</strong>이 됩니다.
      </>,
    ],
  },
];

function StoryParagraph({ children }) {
  return <p className="story-paragraph">{children}</p>;
}

function createCrateredMoonGeometry() {
  const geometry = new THREE.SphereGeometry(1.25, 160, 96);
  const position = geometry.attributes.position;
  const normal = new THREE.Vector3();
  const craters = [
    { direction: new THREE.Vector3(-0.42, 0.28, 0.86).normalize(), radius: 0.24, depth: 0.09 },
    { direction: new THREE.Vector3(0.15, 0.52, 0.84).normalize(), radius: 0.16, depth: 0.065 },
    { direction: new THREE.Vector3(0.46, -0.12, 0.88).normalize(), radius: 0.19, depth: 0.075 },
    { direction: new THREE.Vector3(-0.18, -0.48, 0.86).normalize(), radius: 0.18, depth: 0.07 },
    { direction: new THREE.Vector3(0.64, 0.38, 0.67).normalize(), radius: 0.13, depth: 0.05 },
    { direction: new THREE.Vector3(-0.62, -0.2, 0.76).normalize(), radius: 0.14, depth: 0.05 },
    { direction: new THREE.Vector3(0.08, -0.72, 0.69).normalize(), radius: 0.11, depth: 0.04 },
  ];

  for (let i = 0; i < position.count; i += 1) {
    normal.fromBufferAttribute(position, i).normalize();
    let displacement =
      Math.sin(normal.x * 22.1 + normal.y * 7.7) * 0.008 +
      Math.sin(normal.y * 18.4 - normal.z * 12.3) * 0.007 +
      Math.sin((normal.x + normal.z) * 31) * 0.004;

    craters.forEach(({ direction, radius, depth }) => {
      const distance = Math.acos(THREE.MathUtils.clamp(normal.dot(direction), -1, 1));
      if (distance < radius) {
        const t = distance / radius;
        const bowl = Math.cos(t * Math.PI * 0.5);
        const rim = Math.exp(-Math.pow((t - 0.86) / 0.13, 2));
        displacement -= depth * bowl * bowl;
        displacement += depth * 0.34 * rim;
      }
    });

    normal.multiplyScalar(1.25 + displacement);
    position.setXYZ(i, normal.x, normal.y, normal.z);
  }

  position.needsUpdate = true;
  geometry.computeVertexNormals();
  return geometry;
}

function Moon3D({ scrollProgress }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      alpha: true,
    });
    renderer.setClearColor(0x000000, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(36, 1, 0.1, 100);
    camera.position.set(0, 0, 5.15);

    const moonRoot = new THREE.Group();
    scene.add(moonRoot);

    const ambientLight = new THREE.AmbientLight(0x9fa5b4, 0.16);
    scene.add(ambientLight);
    const keyLight = new THREE.DirectionalLight(0xf4f0df, 5.4);
    keyLight.position.set(-8.6, 0.38, 1.05);
    scene.add(keyLight);
    const rimLight = new THREE.DirectionalLight(0x7480b6, 0.22);
    rimLight.position.set(0, -1.6, -2.4);
    scene.add(rimLight);

    const fallbackMoon = new THREE.Mesh(
      createCrateredMoonGeometry(),
      new THREE.MeshStandardMaterial({
        color: 0xbdb6a5,
        roughness: 0.96,
        metalness: 0,
      })
    );
    moonRoot.add(fallbackMoon);

    const normalizeModel = (object) => {
      const box = new THREE.Box3().setFromObject(object);
      const center = box.getCenter(new THREE.Vector3());
      const size = box.getSize(new THREE.Vector3());
      const maxAxis = Math.max(size.x, size.y, size.z) || 1;
      object.position.sub(center);
      object.scale.setScalar(2.5 / maxAxis);
    };

    const loader = new GLTFLoader();
    loader.load(
      MOON_MODEL_PATH,
      (gltf) => {
        moonRoot.clear();
        normalizeModel(gltf.scene);
        moonRoot.add(gltf.scene);
      },
      undefined,
      () => {
        // public/models/moon.glb 가 아직 없으면 fallback 달을 그대로 보여줍니다.
      }
    );

    let frameId;
    const render = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const width = Math.max(1, canvas.clientWidth);
      const height = Math.max(1, canvas.clientHeight);
      renderer.setPixelRatio(dpr);
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();

      const phase = scrollProgress?.get?.() || 0;
      const angle =
        phase < 0.5
          ? THREE.MathUtils.lerp(-1.42, 0, phase / 0.5)
          : THREE.MathUtils.lerp(0, 1.54, (phase - 0.5) / 0.5);
      const shadowWeight = THREE.MathUtils.smoothstep(phase, 0.42, 1);
      ambientLight.intensity = THREE.MathUtils.lerp(0.17, 0.045, shadowWeight);
      keyLight.intensity = THREE.MathUtils.lerp(5.2, 6.2, shadowWeight);
      keyLight.position.set(
        Math.sin(angle) * 10.5,
        0.38,
        Math.cos(angle) * 5.8
      );
      moonRoot.rotation.x = -0.08;
      renderer.render(scene, camera);
      frameId = requestAnimationFrame(render);
    };

    render();
    return () => {
      cancelAnimationFrame(frameId);
      fallbackMoon.geometry.dispose();
      fallbackMoon.material.dispose();
      renderer.dispose();
    };
  }, [scrollProgress]);

  return <canvas ref={canvasRef} className="story-moon-canvas" aria-hidden />;
}

function StoryChapter({ chapter, articleRef }) {
  return (
    <section
      className={`story-section story-section--chapter ${chapter.sectionClass}`}
    >
      <article ref={articleRef} className="story-chapter">
        <div className="story-chapter-head">
          <div className="story-chapter-mark">{chapter.mark}</div>
          <h2 className="story-chapter-title">{chapter.title}</h2>
        </div>
        {chapter.paragraphs.map((paragraph, index) => (
          <StoryParagraph key={`${chapter.id}-paragraph-${index}`}>
            {paragraph}
          </StoryParagraph>
        ))}
      </article>
    </section>
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
  const [selectedCharacterId, setSelectedCharacterId] = useState(null);
  const ch3ArticleRef = useRef(null);
  const selectedCharacter = CHARACTERS.find(
    (character) => character.id === selectedCharacterId
  );

  // 페이지 전체 스크롤 진행도(0~1)를 WebGL 달 회전에 사용합니다.
  const { scrollYProgress } = useScroll();

  // chapter 3 의 article(본문 영역) 자체를 ref 로 추적해서,
  // 본문이 viewport 가운데에 있을 때 달도 가운데, 본문이 위로 흐르는 만큼
  // 달도 함께 위로 흘러가도록 합니다.
  const { scrollYProgress: ch3Progress } = useScroll({
    target: ch3ArticleRef,
    offset: ["start end", "end start"],
  });
  const moonTop = useTransform(
    ch3Progress,
    MOON_TOP_INPUT,
    MOON_TOP_OUTPUT
  );

  const goLanding = () => {
    if (typeof onNavigate === "function") onNavigate("landing");
  };

  const handleTabClick = (tabId) => {
    setActiveTab(tabId);
    setSelectedCharacterId(null);
  };

  return (
    <div className="story">
      <div className="story-starfield" aria-hidden />

      {/* ───────── 헤더 (랜딩과 같은 디자인이되 배경 투명 + 메뉴 숨김) ───────── */}
      <SiteHeader onNavigate={onNavigate} transparent minimal />

      {/* ───────── ① 표지 (우주, 가장 어두움) ───────── */}
      <section className="story-cover">
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
          <Moon3D scrollProgress={scrollYProgress} />
        </motion.div>

        {STORY_CHAPTERS.map((chapter) => (
          <StoryChapter
            key={chapter.id}
            chapter={chapter}
            articleRef={chapter.trackMoonExit ? ch3ArticleRef : undefined}
          />
        ))}

      </div>{/* /.story-chapters */}

      {/* ───────── 펫 안내 (메이플 직업 페이지 스타일 그리드) ──────── */}
      {/* 캐릭터는 placeholder 4종(스탯 4종 대응). 캐릭터 확정 시 emoji /
          이름 / tag / desc 만 수정하면 됩니다. 이미지가 준비되면 thumb 의
          이모지를 <img src=... /> 로 교체. */}
      <section className="story-section story-section--light story-section--pets">
        <div className="story-pets">
          <div className="story-pets-head">
            <span className="story-eyebrow story-eyebrow--dark">루미나 안내서</span>
            <h2 className="story-pets-title">루미나의 친구들</h2>
            <p className="story-pets-sub">
              알이 된 친구들이 어떤 모습으로 깨어나는지. 곧 당신의 기록이
              그들의 하루를 다시 시작하게 할 거예요.
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
                onClick={() => handleTabClick(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* 활성 탭에 따라 카드 그리드 분기 */}
          {activeTab === "characters" && selectedCharacter ? (
            <div className="story-character-detail">
              <div className="story-evolution">
                {EVOLUTION_STAGES.map((stage, index) => (
                  <div
                    key={`${selectedCharacter.id}-${stage}`}
                    className="story-evolution-step"
                  >
                    <div className="story-evolution-thumb">
                      <span className="story-evolution-emoji" aria-hidden>
                        {selectedCharacter.emoji}
                      </span>
                    </div>
                    <div className="story-evolution-label">{stage}</div>
                    <div className="story-evolution-note">
                      {index === 0 && "작은 떡을 기다리는 첫 모습"}
                      {index === 1 && "습관이 쌓이며 성격이 드러나는 시기"}
                      {index === 2 && "스탯의 흔적을 품고 완성된 모습"}
                    </div>
                  </div>
                ))}
              </div>

              <div className="story-character-detail-info">
                <h3 className="story-character-detail-name">
                  {selectedCharacter.name}
                </h3>
                <p className="story-character-detail-desc">
                  {selectedCharacter.desc}
                </p>
                <dl className="story-character-detail-list">
                  <div>
                    <dt>성장 방식</dt>
                    <dd>합리적인 소비 기록이 쌓일수록 진화 단계가 열립니다.</dd>
                  </div>
                  <div>
                    <dt>마이룸 흔적</dt>
                    <dd>성체가 된 뒤에도 방 안에 작은 기념품과 보너스를 남깁니다.</dd>
                  </div>
                  <div>
                    <dt>성격 메모</dt>
                    <dd>같은 종이라도 스탯 비율에 따라 말투와 분위기가 달라집니다.</dd>
                  </div>
                </dl>
                <button
                  type="button"
                  className="story-detail-back"
                  onClick={() => setSelectedCharacterId(null)}
                >
                  캐릭터 목록으로
                </button>
              </div>
            </div>
          ) : (
            <div className="story-character-grid" key={activeTab}>
              {(activeTab === "characters" ? CHARACTERS : ITEMS).map((c) => {
                const isCharacter = activeTab === "characters";
                return (
                  <article
                    key={c.id}
                    className={`story-character ${
                      isCharacter ? "story-character--clickable" : ""
                    }`}
                    role={isCharacter ? "button" : undefined}
                    tabIndex={isCharacter ? 0 : undefined}
                    onClick={
                      isCharacter ? () => setSelectedCharacterId(c.id) : undefined
                    }
                    onKeyDown={
                      isCharacter
                        ? (event) => {
                            if (event.key === "Enter" || event.key === " ") {
                              event.preventDefault();
                              setSelectedCharacterId(c.id);
                            }
                          }
                        : undefined
                    }
                  >
                    <div className="story-character-thumb">
                      <span className="story-character-emoji" aria-hidden>
                        {c.emoji}
                      </span>
                    </div>
                    <div className="story-character-info">
                      {c.tag && <div className="story-character-tag">{c.tag}</div>}
                      <h3 className="story-character-name">{c.name}</h3>
                      <p className="story-character-desc">{c.desc}</p>
                    </div>
                  </article>
                );
              })}
            </div>
          )}

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
