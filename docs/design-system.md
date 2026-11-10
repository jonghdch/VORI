# VORI 디자인 시스템

> 작성 2026-11-03. 근거와 정리 전 현황은 [design-audit-2026-11-03.md](design-audit-2026-11-03.md), 화면 비교는 [design-audit/before](design-audit/before) ↔ [design-audit/after](design-audit/after).
> 코드: [`frontend/src/styles/tokens.css`](../frontend/src/styles/tokens.css)(값) · [`frontend/src/styles/components.css`](../frontend/src/styles/components.css)(공통 클래스). 둘 다 `index.css` 맨 위에서 불러오므로 모든 화면에 이미 들어 있다.
> 값은 새로 만든 것이 아니라 정리 전에 가장 많이 쓰이던 값으로 맞춘 것이다. 화면을 만들 때 여기 없는 색·라운드·그림자가 필요하면 먼저 토큰을 추가할지 정하고 이 문서를 같이 고친다.

## 1. 토큰

### 색

| 토큰 | 값 | 쓰임 |
|---|---|---|
| `--c-primary` | `#6f9a5c` | 주 버튼·선택 상태·진행 막대·링크 강조 |
| `--c-primary-strong` | `#5b8449` | 눌림·hover, 초록 글자 |
| `--c-primary-soft` | `#eaf3de` | 선택된 칩·완료 배경·아이콘 바탕 |
| `--c-primary-ink` | `#3f6b2e` | 연한 초록 위 글자 |
| `--c-primary-line` | `#c9ddb8` | 선택된 칩 테두리 |
| `--c-bg` | `#f5f3ee` | 페이지 바탕 |
| `--c-surface` | `#fffdf9` | 카드·시트·모달 면 |
| `--c-surface-2` | `#f7f2e9` | 들어간 면 — 보조 버튼·표 머리·입력 묶음 |
| `--c-track` | `#efe8db` | 세그먼트 컨트롤·진행 막대 바탕 |
| `--c-border` / `--c-border-strong` | `#e8e3d8` / `#d8d4ca` | 선 / 손잡이·강한 구분 |
| `--c-text` / `--c-text-2` / `--c-text-3` | `#3d3830` / `#6b6560` / `#9c9488` | 본문 / 보조 / 흐린 글자·placeholder |
| `--c-danger` / `--c-danger-soft` | `#b4524a` / `#fbe5e2` | 탈퇴·삭제·초과 |
| `--c-gold` / `--c-gold-soft` / `--c-gold-ink` | `#e0a33e` / `#fcf0d3` / `#8a5a0c` | 코인·보상 |
| `--c-info` / `--c-info-soft` | `#4f7fb5` / `#e8eff8` | 정보 배지 |
| `--c-data-green·red·orange·blue·sky` | `#7fb069` … | **차트·스탯 점 전용.** 화면 장식에 쓰지 않는다 |

### 모양·깊이

| 토큰 | 값 | 쓰임 |
|---|---|---|
| `--r-sm` | 8px | 배지·작은 타일·막대 |
| `--r-md` | 12px | 버튼·입력 필드·카드 안 타일 |
| `--r-lg` | 16px | 카드 |
| `--r-xl` | 24px | 모달·시트 |
| `--r-full` | 999px | 칩·필터·탭 토글(알약) |
| `--shadow-sm` | 2단 옅은 그림자 | 카드 기본 |
| `--shadow-md` | `0 8px 20px` | 떠 있는 요소 — 팝오버·드롭다운·hover·고정 바 |
| `--shadow-lg` | `0 18px 48px` | 모달·시트 |
| `--shadow-inset` | 안쪽 1px | 입력 필드만 |
| `--shadow-primary` | 초록 글로우 | primary 버튼만 |
| `--ring-primary` / `--ring-line` | 안쪽 2px 초록 / 1px 선 | 선택 테두리(크기가 변하지 않게 그림자로 그린 테두리) |
| `--focus-ring` | 바깥 3px 연초록 | 키보드 포커스·입력 포커스 |
| `--grad-primary` | 초록 3색 160° | 진행 막대 채움·제목 아이콘 배지 |
| `--grad-surface` | 크림 → 베이지 | 히어로 카드(홈 펫 카드·가계부 요약)·사이드바·완료 카드 |

### 간격·레이아웃·글자·움직임

| 토큰 | 값 |
|---|---|
| `--sp-1` … `--sp-8` | 4 · 8 · 12 · 16 · 20 · 24 · 28 · 32px (4px 그리드) |
| `--container-max` | 720px — 모바일·단일 칼럼 콘텐츠 최대 너비 |
| `--page-x` | 16px — 페이지 좌우 여백 |
| `--touch` | 44px — 최소 터치 타깃 |
| `--btn-h` / `--btn-h-sm` / `--btn-h-lg` | 48 / 36 / 54px |
| `--fs-h1` · `--fs-h2` · `--fs-h3` | 24 · 20 · 17px (제목 3단계) |
| `--fs-body` · `--fs-body-sm` | 15 · 13px (본문 2단계) |
| `--fs-caption` | 12px (캡션) |
| `--fs-input` | 16px — 입력 필드 전용(iOS 확대 방지) |
| `--ease` · `--dur` | `cubic-bezier(.2,.8,.2,1)` · 220ms — 전환 1종 |
| `--overlay` · `--overlay-blur` | `rgba(45,51,40,.42)` · 4px — 모달 뒤 |
| `--modal-max` · `--sheet-max` | 420 · 560px |

## 2. 공통 클래스

| 클래스 | 사용 |
|---|---|
| `.btn` + `.btn--primary` · `--secondary` · `--ghost` · `--danger`(+`--danger-solid`) | 모든 버튼. 크기 `.btn--sm` · `.btn--lg`, 꽉 채우기 `.btn--block` |
| `.btn-icon` | 아이콘만 있는 버튼(44×44, 원형) |
| `.card` + `.card--flat` · `--elevated` · `--interactive` | 카드 |
| `.input` · `.select` · `.textarea` | 입력. 글자 16px, 포커스 시 `--focus-ring` |
| `.chip` (+`.is-selected` 또는 `aria-pressed="true"`) | 선택 칩 |
| `.modal-backdrop` + `.modal` | 가운데 다이얼로그 |
| `.modal-backdrop.modal-backdrop--sheet` + `.sheet` + `.sheet-grip` | 아래 시트(768px 이상에서는 가운데 창) |
| `.modal-head` · `.modal-title` · `.modal-close` · `.modal-body` · `.modal-foot` · `.modal-actions`(`--stack`) | 모달·시트 안 |

```jsx
<button type="button" className="btn btn--primary btn--block">저장</button>

<div className="modal-backdrop modal-backdrop--sheet" onMouseDown={closeOnBackdrop}>
  <div className="sheet" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
    <span className="sheet-grip" aria-hidden />
    <header className="modal-head">
      <h2 id={titleId} className="modal-title">카테고리 선택</h2>
      <button type="button" className="modal-close" aria-label="닫기" onClick={onClose}>…</button>
    </header>
    <div className="modal-body">…</div>
    <div className="modal-foot">
      <div className="modal-actions">
        <button className="btn btn--secondary">취소</button>
        <button className="btn btn--primary">확인</button>
      </div>
    </div>
  </div>
</div>
```

기존 화면의 클래스(`.hm-btn`, `.wr-sheet` 같은 것)는 공통 클래스 옆에 붙은 채 남아 **배치(폭·여백·정렬)와 그 화면 고유 콘텐츠만** 맡는다. 모양(색·라운드·그림자·글자 크기·높이)은 공통 클래스에서만 정한다.

## 3. 새 화면을 만들 때 지킬 것

1. 색·라운드·그림자·간격·글자 크기는 `var(--…)` 토큰만 쓴다. hex·px 를 직접 적지 않는다(30px 이상 핵심 숫자·10px 이하 배지 숫자만 예외).
2. 버튼은 `.btn` 계열을 쓴다. 한 화면에 primary 는 하나(주 행동), 나머지는 secondary·ghost.
3. 버튼 라운드는 `--r-md`, 알약 모양(`--r-full`)은 칩·필터·탭 토글에만.
4. 카드는 `--r-lg` + `--shadow-sm` + `--c-surface` 단색. 카드 안의 타일은 `--r-md`, 그림자 없음.
5. 그라디언트는 `--grad-primary`(진행 막대·아이콘 배지)와 `--grad-surface`(히어로 카드)만. 카드 모서리 색 번짐을 새로 만들지 않는다.
6. 색 있는 그림자는 primary 버튼(`--shadow-primary`)만. 안쪽 그림자는 입력 필드(`--shadow-inset`)만.
7. 입력 필드 글자는 16px 이상(`--fs-input`) — 아이폰이 화면을 확대하지 않게.
8. 터치 대상은 44×44px 이상(`--touch`). 아이콘 버튼은 `.btn-icon`.
9. 단일 칼럼 콘텐츠 폭은 `--container-max`, 좌우 여백은 `--page-x`. 카드 사이 간격은 `--sp-4`.
10. 간격은 4px 그리드(`--sp-1..8`). 32px 보다 큰 섹션 여백만 직접 적는다.
11. 차트·스탯 점은 `--c-data-*`. 상태 색(초록·빨강·금색)은 글자와 아이콘을 같이 써 색만으로 뜻을 전하지 않는다.

## 4. 모달 규칙

- **종류는 둘** — 확인·짧은 입력은 가운데 `.modal`(최대 420px), 목록·고르기·긴 내용은 아래 `.sheet`(최대 560px, 768px 이상이면 가운데 창).
- **오버레이** — `--overlay` 한 색 + 4px 블러. 바깥을 눌러 닫을 수 있는 창이면 `onMouseDown` 에서 `e.target === e.currentTarget` 일 때만 닫는다(필수 입력 창 — 펫 이름 짓기 — 은 닫지 않는다).
- **모양** — 라운드 `--r-xl`(시트는 위 모서리만), 그림자 `--shadow-lg`, 면 `--c-surface`, 테두리 `--c-border`.
- **머리줄** — 제목 `.modal-title`(17px·800) 왼쪽, 닫기 `.modal-close` 오른쪽 위(44px). 머리줄이 없는 다이얼로그는 닫기 버튼을 `.modal` 바로 아래 두면 오른쪽 위에 뜬다.
- **여백** — 다이얼로그 안쪽 24px. 시트는 머리 `16/8/8/20`, 본문 `8/20/16`, 아래 `12/20/16 + 안전 영역`.
- **버튼 줄** — `.modal-actions`. 버튼 2개면 가로 2분할(1:1, 취소 왼쪽·확인 오른쪽), 3개 이상이거나 글이 길면 `.modal-actions--stack`(세로, 주 버튼 위). 버튼 1개면 꽉 채운다.
- **움직임** — 열 때 오버레이 fade + 창 rise(16px) 220ms 한 가지. `prefers-reduced-motion` 이면 끈다.
- **겹침 순서(z-index)** 는 화면 CSS 가 정한다 — 펫 이름(200) > 출석(190) 처럼 의미가 있어서다.
- **접근성** — `role="dialog"`·`aria-modal`·`aria-labelledby` 를 단다. 열면 창 안으로 포커스를 옮기고 Esc 로 닫는다(포커스 가두기는 지금 컴포넌트마다 따로 구현돼 있다 — 새 모달도 같은 동작을 넣는다).

## 5. 의도적으로 남긴 예외

| 대상 | 이유 |
|---|---|
| 스토리(`Story/StoryPage.css`) | 3D·우주 배경·챕터 연출. 별·달의 그림자와 그라디언트가 장면 자체라 토큰화하지 않았다 |
| 랜딩 히어로·팀 소개의 어두운 배경, 헤더 버튼(`.landing-btn`) | 마케팅 화면의 투명 헤더 변형이 있어 앱 버튼과 따로 둔다. 라운드·간격·글자 크기는 토큰으로 바꿨다 |
| 마이룸 방 테마 배경(`.pet-room-bg--*`) | 게임 콘텐츠(햇살·숲·밤·구름 테마) |
| 펫 그림 뒤 배경(`.hm-pet-art`), 스켈레톤 shimmer, 도넛 차트(conic) | 펫 연출·로딩 애니메이션·데이터 표현 |
| 사이드바 활성 아이콘의 초록 반짝임(@keyframes) | 애니메이션 프레임 값 |
| 도감 등급·카테고리 색 테두리(`inset 0 0 0 2px #…`) | 등급·카테고리를 구분하는 데이터 색 |
| PC 대시보드 폭(1080~1420px), 관리자 1280px | 데스크톱 레이아웃 고유값 |
| 로그인·가입 카드 폭 480px | 인증 카드 폭(둘을 같게 맞춤) |
