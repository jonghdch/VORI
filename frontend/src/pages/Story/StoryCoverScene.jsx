import { useEffect, useRef } from "react";

// 표지 뒤 배경 장식 — 순수 장식(aria-hidden).
// 은은한 빛 번짐(orb) 두 개와 화면을 가로지르는 빛줄기(streak)만 둡니다.
const STREAK_TOPS = [18, 34, 52, 67, 81];

function StoryCoverScene() {
  const sceneRef = useRef(null);

  // 표지가 화면 밖이면 애니메이션을 멈춥니다. 아래에서 WebGL 달도 그리고 있어서
  // 안 보이는 장식이 계속 돌지 않게 합니다.
  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene || !("IntersectionObserver" in window)) return undefined;
    const observer = new IntersectionObserver(([entry]) => {
      scene.classList.toggle("is-paused", !entry.isIntersecting);
    });
    observer.observe(scene);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={sceneRef} className="story-scene" aria-hidden="true">
      <div className="story-scene-orb story-scene-orb--a" />
      <div className="story-scene-orb story-scene-orb--b" />
      <div className="story-scene-streaks">
        {STREAK_TOPS.map((top, index) => (
          <span
            key={top}
            style={{ top: `${top}%`, animationDelay: `${-index * 1.7}s` }}
          />
        ))}
      </div>
    </div>
  );
}

export default StoryCoverScene;
