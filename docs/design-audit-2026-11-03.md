# 디자인 현황 감사 — 2026-11-03

대상: `frontend/src/**/*.css` 39개 파일(약 19,800줄). 스토리 페이지(`Story/StoryPage.css`, 3D·우주 연출)는 집계에 넣었지만 통일 대상에서는 뺐다(아래 "예외").
집계 스크립트: 선언 단위로 값을 모아 셌다(주석 제외). 정리 후 결과와 규칙은 [design-system.md](design-system.md).
화면 비교: [design-audit/before](design-audit/before) ↔ [design-audit/after](design-audit/after) (iPhone 390×844, 관리자만 1280).

## 요약 — 흩어진 값 종류 수 (스토리 페이지 제외)

| 항목 | 정리 전 (종류 / 선언) | 정리 후 (종류 / 선언) | 정리 후 기준 |
|---|---|---|---|
| border-radius | 36종 / 460 | 13종 / 383 | `--r-sm·md·lg·xl·full` 5개 + `50%`(원) + 모서리 조합 |
| box-shadow | 120종 / 168 | 26종 / 125 | `--shadow-sm·md·lg`, `--shadow-inset`, `--shadow-primary`, `--ring-*`, `--focus-ring` (+상태 테두리 색 몇 개) |
| 그라디언트 | 51종 / 61 | 14종 / 14 | `--grad-primary`, `--grad-surface` 2종 + 예외(랜딩·펫 방 테마·스켈레톤·도넛 차트) |
| 색 리터럴(hex·rgba) | 492종 | 218종 | 의미 토큰 `--c-*` 27개. 남은 리터럴은 데이터 색·투명도(rgba)·랜딩 |
| padding | 244종 / 625 | 140종 / 529 | 4px 그리드 `--sp-1..8` 조합(조합 문자열이 달라 종류 수는 남음) |
| font-size | 44종 / 835 | 28종 / 725 | 제목 3 · 본문 2 · 캡션 1 + 입력 16px. 남은 것은 30px 이상 핵심 숫자·히어로·10px 이하 배지 |
| 버튼 모양 계열 | 15계열 이상 (아래) | `.btn` 1계열 + 변형 | primary/secondary/ghost/danger, sm/lg/block |
| 모달·시트 구현 | 8종 (각자 오버레이·라운드·그림자·애니메이션) | `.modal`·`.sheet` 2종 | 오버레이 1색 + 블러, 라운드 `--r-xl`, 그림자 `--shadow-lg`, 애니메이션 1종 |
| 페이지별 색 변수 세트 | 11세트 (`--home-*`, `--hm-*`, `--sp-*`, `--mr-*`, `--wr-*`, `--lg-*`, `--admin-*`, `--legal-*`, 랜딩·가입·로그인의 `--bg/--text…`) | 1세트 (`tokens.css`) | 별칭 135개 정의 삭제, 사용처 1,553곳을 토큰으로 바꿈 |

## 색 — 같은 역할에 쓰이던 값들

| 역할 | 흩어져 있던 값 | 통일 값(토큰) |
|---|---|---|
| 브랜드 초록 | `#7fb069`(--home-green), `#7c9e6b`(--green·--lg-sage), `#6f9a5c`(--hm/sp/mr/wr-sage), `#7c8b5c`(--admin-green), `#5f9548`(--hm-green) | `--c-primary #6f9a5c` — 사용량은 `#7fb069`와 거의 같고(52:51) 흰 글자 대비가 더 높아 골랐다 |
| 진한 초록 | `#5e8c4e`, `#5b8449` | `--c-primary-strong #5b8449` |
| 연한 초록 바탕 | `#eaf3de`, `#e8f2e3`, `#e9f2df`, `#e8f2df` | `--c-primary-soft #eaf3de` |
| 본문 글자 | `#3d3830`, `#2d3328`, `#2b2b2b`, `#3a3835` | `--c-text #3d3830` |
| 보조 글자 | `#6b6560`, `#6e6b65`, `#7a8072`, `#7a7268`, `#6b6359` | `--c-text-2 #6b6560` (따뜻한 회색 계열로 — 본문색과 같은 톤) |
| 흐린 글자 | `#9c9488`, `#a39a8d`, `#8f8679` | `--c-text-3 #9c9488` |
| 선 | `#e8e3d8`, `#e2dfd7`, `#ece4d6`, `#ebe5d9`, `#e6ebe0`, `#e6dfd1` | `--c-border #e8e3d8` |
| 페이지 바탕 | `#f5f3ee`, `#f8f9f5` | `--c-bg #f5f3ee` |
| 들어간 면 | `#f7f2e9`, `#f4f0e8`, `#f3ede2`, `#fbfaf6`, `#faf7f0` | `--c-surface-2 #f7f2e9` |
| 위험(빨강) | `#b4524a`, `#b4432f`, `#c94a4a`, `#b23b3b`, `#a8352c`, `#d9645a` | `--c-danger #b4524a` |

## 버튼 변형 — 계열별

| 계열(파일) | 라운드 | 주 버튼 색 | 비고 |
|---|---|---|---|
| `.home-btn` (AppShell) | 12px | `--home-green #7fb069`, 어두운 `#4a4f45` | 홈 대시보드에서 `#7c9e6b`·`#3d3830` 로 다시 덮어씀 |
| `.hm-btn` (HomeScreen) | 12px, 홈·가계부에서는 999px | 짙은 갈색, 홈·가계부에서는 세이지 + 초록 그림자 | 같은 클래스가 화면마다 모양이 다름 |
| `.wr-btn` (WalletRecord) | 12px | 짙은 갈색 | 지출 기록 저장 |
| `.sp-btn`, `.sp-buy` (Shop) | 16px / 999px | 세이지 + 그림자, 인벤토리는 금색 그라디언트 | |
| `.mr-btn` (MyRoom) | 999px | 세이지 + 그림자, 배웅은 금색 그라디언트 | |
| `.signup-submit`, `.login-submit` | 12px | `#7c9e6b` | |
| `.settings-save-btn`, `.settings-withdraw-btn` | 9px | `#3a3835`, 탈퇴 `#b23b3b` | |
| `.profile-save`/`.profile-cancel` | 8px | `#3d3830` | 설정 > 프로필 |
| `.adm-btn`, `.sanction-submit`, `.title-submit`, `.rule-save` | 8px | `#7c8b5c` 올리브 | 관리자 |
| `.attendance-cta` | 999px | 초록 그라디언트 | 출석 팝업 |
| `.ach-btn-*` | 10px | `--home-green` | 칭호 |
| `.ja-btn-line` | 999px | — (보조) | 판정 화면 |
| `.landing-btn` | 10px / 12px | `#7c9e6b` | 랜딩 헤더(예외로 남김) |

정리 후: 모든 사용자 화면·모달·관리자 버튼이 `.btn`(+기존 클래스는 배치용으로만) — 라운드 `--r-md`, 높이 48px(작게 36px·크게 54px), primary 는 `--c-primary` + `--shadow-primary`.

## 카드 변형

| 카드 | 라운드(전) | 그림자(전) | 배경(전) |
|---|---|---|---|
| `.home-card`/`.home-kpi` | 14px | `0 1px 0 …04`, `0 4px 14px …05` | 모서리 색 번짐(radial) + 크림 그라디언트 |
| `.hm-card` | 14px / 홈·가계부 22px | `0 2px 10px …04` / `0 2px 12px …06` | 단색 / 오른쪽 위 세이지 번짐 |
| `.sp-card`, `.mr-card`, `.wr-card` | 22px | `0 2px 12px …06` | 단색, 알 카드는 등급색 번짐 |
| `.ach-card`, `.dex-card` | 12px | 없음 | 완료 시 초록 그라디언트 |
| `.login-card`, `.signup-card` | 16px | `0 8px 28px …04` | 단색 |
| `.adm-card`, `.admin-dash-card` | 14px | `0 1px 0 …04` | 단색 |
| `.ledger-*-card` (가계부 PC) | 14px | `0 4px 14px …05` | 번짐 + 그라디언트 |

정리 후: 최상위 카드는 `--r-lg` + `--shadow-sm` + `--c-surface` 단색, 카드 안의 작은 타일만 `--r-md`.

## 모달·시트 — 8종

| 모달 | 파일 | 오버레이 | 라운드 | 그림자 | 애니메이션 | 닫기 |
|---|---|---|---|---|---|---|
| 펫 이름 짓기 | `PetNameGate` | `rgba(45,51,40,.48)` | 14px | 2단 | fade+rise 0.22s | 없음(필수) |
| 출석 | `AttendanceModal` | `rgba(45,51,40,.48)` | 14px | 2단 | fade+rise | 오른쪽 위 원형 × |
| 구매 내역 | `PurchaseHistoryModal` | `rgba(45,51,40,.48)` | 14px | 2단 | fade+rise | 머리줄 × |
| 구매 확인(상점) | `ShopPage` 시트 | `rgba(40,36,30,.42)` | 28px(위만) | Tailwind식 2단 | fade+rise 0.2s | 없음, 버튼 96px+1fr |
| 가계부 시트(필터·검색·상세) | `LedgerSheet` | `rgba(45,51,40,.45)` | 20px | `0 18px 48px .2` | 아래 시트만 rise | 아이콘 버튼 |
| 지출 입력 시트(카테고리·그날 기록) | `WalletEntryPage` | `rgba(40,36,30,.45)` | 22px(위만) | `0 -8px 30px` | fade+rise 0.2s | 아이콘 버튼 |
| 판정 확인 | `WalletAnalysisPage` | `rgba(61,56,48,.42)` | `.hm-card` 빌림 | 카드 그림자 | 없음 | 없음, 1:1.2 |
| (미사용) 가계부 확인 | `WalletEntry.css` `.ledger-confirm-*` | — | — | — | — | 코드에서 안 씀 → 삭제 |

팝오버 2종(알림 `NotificationBell`, 내 정보 `AccountMenu`)은 모달이 아니라 `--shadow-md` + `--r-lg` 로 맞췄다.
펫 배웅·탈퇴 확인·가구 회수는 `confirm()`/화면 안 펼침이라 이번 범위 밖(남은 작업 참고).

## 컨테이너 너비

모바일 단일 칼럼 콘텐츠: `720px`(홈·가계부·상점·마이룸·목록, 8곳 — 가장 많음), `560px`(판정·지출 입력 옛 화면), `640px`(지출 기록 PC), `760px`(약관) → 모두 `--container-max 720px`.
로그인 440px·가입 480px 카드 → 480px 로 같게. PC 대시보드 폭(1080~1420px)과 관리자 1280px 은 레이아웃 고유값으로 남김.

## 값별 상세 (정리 전, 많이 쓰인 순)

### 모서리 라운드 (border-radius) — 서로 다른 값 36종, 선언 474개

| 값 | 횟수 | 파일 (횟수) |
|---|---:|---|
| `999px` | 117 | ShopScreen(13), WalletPage(10), WalletScreen(10), LandingPage(8) … |
| `50%` | 71 | WalletPage(9), LandingPage(8), WalletScreen(6), WalletAnalysisScreen(6) … |
| `10px` | 45 | WalletPage(5), HomeScreen(4), WalletRecord(4), LandingPage(3) … |
| `8px` | 42 | PetPage(6), LandingPage(4), AdminCommon(3), UsersPage(3) … |
| `12px` | 37 | LandingPage(6), WalletRecord(5), PetPage(3), SignupPage(3) … |
| `14px` | 36 | MyRoom(6), WalletEntry(6), WalletScreen(4), DashboardPage(2) … |
| `var(--home-radius-sm)` | 30 | PetPage(10), AchievementPage(5), PetDexPage(4), WalletPage(4) … |
| `inherit` | 10 | HomeScreen(3), MyRoom(2), PetPage(1), PetDexPage(1) … |
| `16px` | 10 | LandingPage(4), HomeScreen(1), PetPage(1), ShopScreen(1) … |
| `var(--home-radius)` | 9 | AttendanceModal(2), AccountMenu(1), AppShell(1), NotificationBell(1) … |
| `20px` | 9 | LandingPage(2), AppShell(1), PetNameGate(1), HomeScreen(1) … |
| `6px` | 8 | WalletPage(2), SanctionsPage(1), TitleManagePage(1), LoginPage(1) … |
| `18px` | 7 | StoryPage(3), HomeScreen(1), LoginPage(1), ShopScreen(1) … |
| `9px` | 6 | SettingsPage(5), ProfileSettingsPanel(1) |
| `4px` | 5 | PetPage(2), ReportPage(1), WalletPage(1), WalletRecord(1) |
| `2px` | 4 | AppShell(1), PetPage(1), StoryPage(1), WalletScreen(1) |
| `22px` | 4 | AttendanceModal(1), LandingPage(1), PetPage(1), WalletRecord(1) |
| `0` | 3 | PetPage(2), AppShell(1) |
| `7px` | 2 | AdminCommon(1), UsersPage(1) |
| `3px 3px 0 0` | 2 | HomeDashboard(1), WalletPage(1) |
| `24px` | 2 | LandingPage(1), PetPage(1) |
| `5px` | 1 | AttendanceModal(1) |
| `6px 6px 0 0` | 1 | DashboardPage(1) |
| `0 0 3px 0` | 1 | HomeDashboard(1) |
| `50% 50% 0 0` | 1 | PetPage(1) |
| … 나머지 11종 | 11 | |

### 그림자 (box-shadow) — 서로 다른 값 124종, 선언 172개

| 값 | 횟수 | 파일 (횟수) |
|---|---:|---|
| `none` | 16 | PetPage(5), AppShell(2), MyRoom(2), ShopScreen(2) … |
| `0 1px 0 rgba(0, 0, 0, 0.04)` | 6 | DashboardPage(2), AdminCommon(1), AdminLayout(1), UsersPage(1) … |
| `inset 0 0 0 2px var(--home-green-dark)` | 4 | RecordCalendar(2), AttendanceModal(1), PetDexPage(1) |
| `0 4px 14px rgba(61, 56, 48, 0.05)` | 4 | HomeDashboard(2), WalletPage(2) |
| `inset 0 0 0 1px rgba(61, 56, 48, 0.05)` | 4 | WalletPage(2), HomeDashboard(1), LandingPage(1) |
| `0 1px 2px rgba(45, 51, 40, 0.08), 0 18px 48px rgba(45, 51, 40, 0.22)` | 3 | AttendanceModal(1), PetNameGate(1), PurchaseHistoryModal(1) |
| `inset 0 0 0 2px var(--home-green)` | 3 | AttendanceModal(2), WalletPage(1) |
| `0 4px 10px rgba(124, 158, 107, 0.25)` | 3 | WalletPage(2), HomeDashboard(1) |
| `0 4px 12px rgba(111, 154, 92, 0.3)` | 3 | HomeScreen(1), MyRoom(1), ShopScreen(1) |
| `0 16px 40px rgba(45, 51, 40, 0.16)` | 2 | AccountMenu(1), NotificationBell(1) |
| `0 0 4px 1px rgba(127, 201, 90, 0.9), 0 0 12px 3px rgba(127, 201, 90, 0.45)` | 2 | AppShell(2) |
| `0 1px 2px rgba(0, 0, 0, 0.05)` | 2 | AdminCommon(1), UsersPage(1) |
| `inset 0 0 0 1px #d8e8c9` | 2 | HomeScreen(2) |
| `0 8px 28px rgba(61, 56, 48, 0.04)` | 2 | LoginPage(1), SignupPage(1) |
| `inset 0 0 0 1px var(--home-green)` | 2 | PetPage(1), ReportPage(1) |
| `0 2px 8px rgba(61, 56, 48, 0.1)` | 2 | ShopScreen(1), WalletScreen(1) |
| `var(--sp-shadow-up)` | 2 | ShopScreen(2) |
| `inset 0 0 0 1px #efe5d2` | 2 | WalletPage(2) |
| `inset 0 0 0 1.5px var(--hm-ink)` | 2 | WalletScreen(2) |
| `inset 0 0 0 1.5px #c9ddb8` | 2 | WalletScreen(2) |
| `0 8px 20px rgba(45, 51, 40, 0.14)` | 1 | AppShell(1) |
| `0 16px 40px rgba(45, 51, 40, 0.18)` | 1 | AppShell(1) |
| `0 3px 10px rgba(61, 56, 48, 0.07)` | 1 | AppShell(1) |
| `0 0 14px 5px rgba(127, 201, 90, 0.6)` | 1 | AppShell(1) |
| `0 0 6px 2px rgba(127, 201, 90, 1), 0 0 18px 5px rgba(127, 201, 90, 0.55)` | 1 | AppShell(1) |
| … 나머지 99종 | 99 | |

### 그라디언트 — 서로 다른 값 71종, 선언 84개

| 값 | 횟수 | 파일 (횟수) |
|---|---:|---|
| `linear-gradient(180deg, #fffdf9 0%, #f8f5ee 100%)` | 6 | WalletPage(3), HomeDashboard(2), AppShell(1) |
| `linear-gradient(160deg, #8ec277 0%, var(--home-green) 55%, var(--home-green-dark) 100%)` | 3 | AttendanceModal(2), PetPage(1) |
| `linear-gradient(180deg, #fffdf9 0%, #f3f8ef 100%)` | 2 | AchievementPage(1), PetDexPage(1) |
| `radial-gradient(circle at 100% 0%, var(--kpi-tint) 0%, rgba(255, 253, 249, 0) 55%)` | 2 | HomeDashboard(1), WalletPage(1) |
| `radial-gradient(circle, rgba(255, 255, 255, 0.95) 0 1px, transparent 1.8px)` | 2 | StoryPage(2) |
| `radial-gradient(circle, rgba(255, 255, 255, 0.72) 0 1px, transparent 1.7px)` | 2 | StoryPage(2) |
| `radial-gradient(circle, rgba(255, 255, 255, 0.82) 0 1px, transparent 1.8px)` | 2 | StoryPage(2) |
| `radial-gradient(circle at 100% 0%, var(--sec-tint) 0%, rgba(255, 253, 249, 0) 42%)` | 2 | WalletPage(2) |
| `radial-gradient(circle at 0% 0%, rgba(124, 158, 107, 0.14), transparent 55%)` | 1 | AttendanceModal(1) |
| `radial-gradient(rgba(124, 158, 107, 0.14) 1px, transparent 1.6px)` | 1 | AttendanceModal(1) |
| `radial-gradient(circle at 50% 56%, rgba(212, 232, 194, 0.8) 0%, rgba(212, 232, 194, 0) …` | 1 | HomeDashboard(1) |
| `radial-gradient(circle at 92% 6%, rgba(220, 238, 244, 0.95) 0%, rgba(220, 238, 244, 0) …` | 1 | HomeDashboard(1) |
| `radial-gradient(circle at 4% 100%, rgba(251, 235, 205, 0.8) 0%, rgba(251, 235, 205, 0) …` | 1 | HomeDashboard(1) |
| `radial-gradient(rgba(124, 158, 107, 0.12) 1px, transparent 1.6px)` | 1 | HomeDashboard(1) |
| `linear-gradient(180deg, #fffdf9 0%, #f5f3ee 100%)` | 1 | HomeDashboard(1) |
| `linear-gradient(to right, rgba(45, 51, 40, 0.2), rgba(45, 51, 40, 0))` | 1 | HomeDashboard(1) |
| `radial-gradient(circle at 100% 0%, var(--sec-tint) 0%, rgba(255, 253, 249, 0) 45%)` | 1 | HomeDashboard(1) |
| `linear-gradient(90deg, #b8dbec, #7cb9c8)` | 1 | HomeDashboard(1) |
| `linear-gradient(90deg, var(--hm-sunk), #faf7f1, var(--hm-sunk))` | 1 | HomeScreen(1) |
| `radial-gradient(circle at 100% 0%, var(--hm-sage-soft) 0%, rgba(255, 253, 249, 0) 45%)` | 1 | HomeScreen(1) |
| … 나머지 51종 | 51 | |

### 최대 너비 (max-width) — 서로 다른 값 37종, 선언 79개

| 값 | 횟수 | 파일 (횟수) |
|---|---:|---|
| `100%` | 11 | HomeScreen(3), MyRoom(3), WalletRecord(2), AccountMenu(1) … |
| `720px` | 8 | HomeScreen(2), WalletScreen(2), LandingPage(1), MyRoom(1) … |
| `1280px` | 7 | LandingPage(3), AdminCommon(1), AdminLayout(1), DashboardPage(1) … |
| `none` | 5 | AppShell(2), AttendanceModal(1), HomeScreen(1), WalletScreen(1) |
| `560px` | 4 | LandingPage(1), SignupPage(1), WalletAnalysisScreen(1), WalletEntry(1) |
| `760px` | 4 | LandingPage(3), LegalPages(1) |
| `1120px` | 3 | WalletScreen(2), HomeScreen(1) |
| `140px` | 2 | AppShell(1), HomeDashboard(1) |
| `620px` | 2 | AttendanceModal(1), LandingPage(1) |
| `360px` | 2 | AdminCommon(1), HomeDashboard(1) |
| `448px` | 2 | HomeDashboard(2) |
| `500px` | 2 | LandingPage(2) |
| `1100px` | 2 | LandingPage(1), StoryPage(1) |
| `1080px` | 2 | LandingPage(1), ShopScreen(1) |
| `calc(100vw - 40px)` | 1 | AppShell(1) |
| `44px` | 1 | DashboardPage(1) |
| `380px` | 1 | HomeDashboard(1) |
| `700px` | 1 | LandingPage(1) |
| `590px` | 1 | LandingPage(1) |
| `880px` | 1 | LandingPage(1) |
| … 나머지 17종 | 17 | |

### 안쪽 여백 (padding) — 서로 다른 값 264종, 선언 654개

| 값 | 횟수 | 파일 (횟수) |
|---|---:|---|
| `0` | 69 | LandingPage(5), PetPage(5), PetDexPage(5), WalletAnalysisScreen(5) … |
| `16px` | 18 | WalletPage(4), LandingPage(2), ShopScreen(2), AccountMenu(1) … |
| `8px 16px` | 16 | AdminCommon(3), UsersPage(3), LandingPage(3), WalletEntry(3) … |
| `10px 12px` | 14 | MyRoom(3), PetPage(2), SettingsPage(2), AccountMenu(1) … |
| `12px 16px` | 14 | HomeScreen(2), LoginPage(2), SignupPage(2), AppShell(1) … |
| `20px` | 14 | LandingPage(3), WalletAnalysisScreen(2), AttendanceModal(1), PetNameGate(1) … |
| `14px` | 14 | WalletAnalysisScreen(5), HomeScreen(2), WalletRecord(2), LandingPage(1) … |
| `8px 12px` | 13 | LandingPage(3), AppShell(2), PetPage(2), WalletEntry(2) … |
| `12px` | 12 | WalletPage(3), WalletRecord(2), AttendanceModal(1), HomeDashboard(1) … |
| `8px` | 11 | WalletEntry(3), WalletPage(2), AppShell(1), AdminLayout(1) … |
| `4px` | 9 | ShopScreen(4), AdminCommon(1), UsersPage(1), HomeScreen(1) … |
| `2px 8px` | 8 | PetDexPage(2), ShopScreen(2), AdminCommon(1), UsersPage(1) … |
| `24px` | 8 | ShopScreen(2), AdminLayout(1), AiUsagePage(1), LandingPage(1) … |
| `8px 8px` | 7 | AppShell(3), LoginPage(2), SignupPage(1), WalletPage(1) |
| `12px 14px` | 7 | AchievementPage(1), LegalPages(1), PetPage(1), PetDexPage(1) … |
| `0 16px` | 6 | ShopScreen(2), App(1), LandingPage(1), MyRoom(1) … |
| `14px 16px` | 6 | AppShell(1), AchievementPage(1), LegalPages(1), PetPage(1) … |
| `18px` | 6 | AttendanceModal(1), HomeScreen(1), MyRoom(1), PetPage(1) … |
| `16px 18px` | 6 | AchievementPage(3), LandingPage(1), PetDexPage(1), ShopScreen(1) |
| `10px` | 6 | WalletAnalysisScreen(3), HomeScreen(1), WalletPage(1), WalletScreen(1) |
| … 나머지 244종 | 390 | |

### 글자 크기 (font-size) — 서로 다른 값 48종, 선언 870개

| 값 | 횟수 | 파일 (횟수) |
|---|---:|---|
| `13px` | 192 | WalletEntry(19), PetPage(18), WalletScreen(15), LandingPage(12) … |
| `12px` | 180 | LandingPage(17), WalletScreen(13), WalletAnalysisScreen(13), PetPage(12) … |
| `14px` | 142 | WalletEntry(15), LandingPage(13), HomeDashboard(11), WalletPage(10) … |
| `11px` | 70 | WalletAnalysisScreen(12), HomeScreen(5), PetDexPage(5), ShopScreen(5) … |
| `15px` | 53 | LandingPage(7), HomeDashboard(5), StoryPage(5), WalletEntry(5) … |
| `16px` | 52 | AdminCommon(6), UsersPage(6), RationalityRulesPage(5), LandingPage(5) … |
| `22px` | 25 | LandingPage(5), WalletEntry(5), AdminLayout(2), PurchaseHistoryModal(1) … |
| `18px` | 24 | LandingPage(5), ShopScreen(4), PetPage(3), AttendanceModal(2) … |
| `20px` | 15 | WalletPage(4), LandingPage(2), MyRoom(2), AccountMenu(1) … |
| `17px` | 15 | LandingPage(2), StoryPage(2), WalletPage(2), WalletRecord(2) … |
| `19px` | 10 | AccountMenu(1), AttendanceModal(1), PetNameGate(1), PurchaseHistoryModal(1) … |
| `10px` | 10 | AttendanceModal(2), LandingPage(2), NotificationBell(1), HomeDashboard(1) … |
| `30px` | 8 | PetPage(3), AttendanceModal(2), LegalPages(1), StoryPage(1) … |
| `24px` | 8 | PurchaseHistoryModal(1), LegalPages(1), LoginPage(1), PetPage(1) … |
| `26px` | 6 | StoryPage(2), HomeDashboard(1), LandingPage(1), LoginPage(1) … |
| `12.5px` | 5 | AttendanceModal(3), LegalPages(1), PetPage(1) |
| `52px` | 5 | PetNameGate(1), HomeScreen(1), LandingPage(1), StoryPage(1) … |
| `28px` | 5 | PetDexPage(2), DashboardPage(1), PetPage(1), StoryPage(1) |
| `34px` | 4 | LandingPage(3), MyRoom(1) |
| `21px` | 3 | LandingPage(2), WalletEntry(1) |
| `clamp(34px,4vw,49px)` | 3 | LandingPage(3) |
| `27px` | 2 | AttendanceModal(1), PetDexPage(1) |
| `8px` | 2 | AttendanceModal(2) |
| `56px` | 2 | HomeDashboard(1), PetDexPage(1) |
| `32px` | 2 | LandingPage(1), StoryPage(1) |
| … 나머지 23종 | 27 | |

