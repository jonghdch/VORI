import SiteHeader from "../../components/SiteHeader";
// SiteHeader 가 .landing-header 등 랜딩 페이지의 헤더 클래스를 그대로 쓰기
// 때문에, 이 페이지에서도 LandingPage.css 를 함께 import 합니다.
import "../Landing/LandingPage.css";
import "./StoryPage.css";

// 스토리 페이지.
// 흐름은 "우주(가장 어두움) → 별로 다가감 → 도착(베이지)" 순으로 점진적으로
// 밝아져요. 구간별로 .story-section--{단계} 클래스를 두어 배경/글자 색을
// 조절합니다.
//
// 콘텐츠를 손볼 땐 각 <article className="story-chapter"> 안의 텍스트만
// 수정하면 됩니다. 챕터 추가/제거 시에는 section 단위로 통째로 다루세요.
function StoryPage({ onNavigate }) {
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
            펫이 사는 별,<br />
            그리고 함께 자라는 우리
          </h1>
          <p className="story-cover-sub">
            세계관 · 스토리 · 그리고 우리가 이 모든 걸 만든 이유.
          </p>
          <div className="story-cover-hint" aria-hidden>
            ↓ 스크롤
          </div>
        </div>
      </section>

      {/* ───────── ② Chapter 01 (짙은 남청) ───────── */}
      <section className="story-section story-section--dark">
        <article className="story-chapter">
          <div className="story-chapter-mark">Chapter 01</div>
          <h2 className="story-chapter-title">펫이 사는 곳</h2>
          <p className="story-paragraph">
            여기는 ‘VORI’ 라는 작은 별. 그곳에는 사용자의 소비 기록을 양식
            삼아 자라는 작은 생명, <em>펫</em>이 살고 있어요. 펫은 합리적인
            선택을 좋아하고, 과한 지출 앞에선 시무룩해집니다. 매일의 작은
            결정 하나하나가 펫의 컨디션과 모습을 바꿔놓아요.
          </p>
          <p className="story-paragraph">
            펫이 사는 별엔 풍경이 따로 있지 않습니다. 우리가 어떻게 쓰는지에
            따라 그날의 하늘 색이 바뀌고, 마이룸의 공기가 달라져요. 합리적인
            소비가 쌓이면 별 위에 작은 풀잎이 돋고, 무심한 지출이 이어지면
            풀잎이 조용히 자리를 내어줍니다.
          </p>
        </article>
      </section>

      {/* ───────── ③ Chapter 02 (트와일라잇, 점점 밝아짐) ───────── */}
      <section className="story-section story-section--twilight">
        <article className="story-chapter">
          <div className="story-chapter-mark">Chapter 02</div>
          <h2 className="story-chapter-title">펫과 함께한 시간</h2>
          <p className="story-paragraph">
            펫은 처음엔 새싹처럼 작아요. 첫 지출 기록부터 호기심을 보이고,
            합리적인 소비가 쌓일 때마다
            <strong> 에너지 · 매력 · 지능 · 지구력</strong> 스탯이 한 칸씩
            채워집니다.
          </p>
          <p className="story-paragraph">
            어떤 날은 카페를 두 번이나 다녀온 주인에게 “오늘은 아메리카노 한
            잔만!” 하고 슬쩍 귀띔하기도 해요. 또 어떤 날엔 충동구매를 망설이는
            주인 옆에 가만히 앉아 함께 생각해 주기도 하고요.
          </p>
          <p className="story-paragraph">
            그렇게 함께 보낸 시간들이 청소년기를 지나 성체로, 그리고 그 너머의
            모습으로 펫을 데려갑니다. 펫의 진화는 결국,
            <em> 당신이 지난 시간을 어떻게 살아왔는지의 기록</em> 이에요.
          </p>
        </article>
      </section>

      {/* ───────── ④ Chapter 03 (도착, 베이지) ───────── */}
      <section className="story-section story-section--light">
        <article className="story-chapter">
          <div className="story-chapter-mark">Chapter 03</div>
          <h2 className="story-chapter-title">우리가 만든 이유</h2>
          <p className="story-paragraph">
            가계부는 결과가 잘 보이지 않아서 금세 지칩니다. 숫자만 쌓이고,
            그 숫자가 내 일상에 어떤 의미인지 손에 잡히지 않아요.
          </p>
          <p className="story-paragraph">
            VORI 는 매일의 작은 선택이 펫의 성장으로 이어지도록 만들었어요.
            합리적인 소비는 기록 그 자체로 보상이 되고, 그렇지 않은 날에는
            펫이 가볍게 귀띔해줍니다. 가계부의 부담을 덜고, 키우는 재미로
            이어가는 것 — 그게 VORI 가 지향하는 모습입니다.
          </p>
          <p className="story-signature">— 졸업작품 · VORI Team</p>

          <div className="story-back">
            <button
              type="button"
              className="story-back-btn"
              onClick={goLanding}
            >
              메인으로 돌아가기
            </button>
          </div>
        </article>
      </section>
    </div>
  );
}

export default StoryPage;
