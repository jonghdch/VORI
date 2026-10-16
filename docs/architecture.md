# 시스템 아키텍처

> VORI 의 시스템 경계·구성요소·외부 의존·통신 방식. 코드 안 보고도 큰 그림을 잡을 수 있게.

## 한 줄 요약

지출+사유 → 합리성 시그널(🟢⚪🔴) + 펫 스탯 가감을 반환하는 자기관리 서비스. 졸업작품.

## 시스템 구성

```
┌─────────────────────────────────────────────────────────────┐
│                     사용자 (브라우저)                          │
└────────────────────────┬────────────────────────────────────┘
                         │  HTTP/JSON + JSESSIONID 쿠키
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Frontend (React, :3000)                                     │
│  - CRA dev server (운영 시 정적 빌드)                          │
│  - fetch + credentials:'include' 로 백엔드 호출               │
└────────────────────────┬────────────────────────────────────┘
                         │  CORS 허용 origin: http://localhost:3000
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Backend (Spring Boot 3.5 + Java 17, :8080)                  │
│  - Spring Security (세션 인증, BCrypt)                        │
│  - Spring Data JPA (Hibernate 6)                             │
│  - Flyway (스키마 마이그레이션 자동 적용)                       │
└────┬──────────────────────┬───────────────────┬──────────────┘
     │ JDBC                 │ HTTPS             │ HTTPS
     ▼                      ▼                   ▼
┌──────────────┐  ┌──────────────────────┐  ┌──────────────────┐
│ MySQL 8.x    │  │ Google Gemini API    │  │ Google tokeninfo │
│ :3306        │  │ 질문·사유 분류·영수증 │  │ (구글 로그인     │
│ DB: vori     │  │ 인식·펫 대화·임베딩   │  │  토큰 검증)      │
└──────────────┘  └──────────────────────┘  └──────────────────┘
```

## 컴포넌트별 책임

| 컴포넌트 | 책임 | 상태 |
|---|---|---|
| Frontend (React) | 화면·폼·라우팅·세션 쿠키 전송 | 구현됨 — 아래 라우팅 표 |
| Backend (Spring Boot) | REST API·인증·비즈 로직·DB 접근 | 구현됨 — 가계부·하루 판정·AI 질문·펫·상점·마이룸·리포트·출석·업적·관리자 |
| MySQL | 영속 데이터 저장 | Flyway 마이그레이션(`backend/src/main/resources/db/migration/`)이 스키마를 만든다 — [`DATABASE.md`](../DATABASE.md) |
| Google Gemini | AI 질문 문구·사유 분류·영수증 인식·펫 대화·일일 코멘트·카테고리 임베딩 | **연결됨** (`gemini.api.key` = `.env` 의 `GEMINI_API_KEY`. 키 없으면 호출 시 403) |

> 영수증 OCR 은 처음 계획한 Google Vision 이 아니라 **Gemini 이미지 입력**으로 구현했다([`domain.md`](domain.md)). Vision 은 쓰지 않는다.

## 포트·URL

| 서비스 | 로컬 URL |
|---|---|
| 프론트엔드 | http://localhost:3000 |
| 백엔드 API | http://localhost:8080/api |
| MySQL | localhost:3306 (DB 이름 `vori`) |

배포는 AWS EC2 + Nginx 로 프론트와 `/api` 를 한 도메인에서 내보낼 예정이다(10/12 결정). 배포 절차 문서는 아직 없다.

## 프론트엔드 라우팅

React Router v7 (`BrowserRouter`) 사용. SPA 이지만 URL 이 페이지마다 바뀌어 브라우저 뒤로가기·새로고침·링크 공유 모두 정상 동작.

| 경로 | 페이지 | 인증 |
|---|---|---|
| `/` | LandingPage | 공개 |
| `/login` | LoginPage | 공개 |
| `/signup` | SignupPage | 공개 |
| `/signup/profile` | SignupProfilePage (소비 프로필 설문) | **인증 필요** |
| `/onboarding` | OnboardingPage | **인증 필요** |
| `/story` | StoryPage | 공개 |
| `/terms`, `/privacy` | 약관·개인정보처리방침 | 공개 |
| `/home` | HomeDashboard | **인증 필요** (미인증 시 `/login` 으로 리다이렉트) |
| `/wallet` | WalletPage (가계부 달력/조회) | **인증 필요** |
| `/wallet/new` | WalletEntryPage (작성 Step 1 · 입력) | **인증 필요** |
| `/wallet/new/confirm` | WalletConfirmPage (작성 Step 2 · 확인) | **인증 필요** |
| `/wallet/analysis` | WalletAnalysisPage (AI 사유 질문·일일 판정. 작성 흐름과 별개) | **인증 필요** |
| `/report` | ReportPage (주·월 소비 리포트) | **인증 필요** |
| `/myroom` | PetPage (마이룸·펫 성장·가구 배치). 예전 주소 `/raise` 는 여기로 리다이렉트 | **인증 필요** |
| `/dex`, `/dex/:appearanceKey` | 펫·업적·칭호 도감 | **인증 필요** |
| `/shop` | ShopPage (알·가구 상점) | **인증 필요** |
| `/settings/:tab` | SettingsPage (프로필·기본 설정·카드·계좌·계정 탭. `/settings` 는 `/settings/profile` 로) | **인증 필요** |
| `/admin` (→ `/admin/dashboard`) | AdminLayout + 중첩 라우트 (종합 대시보드·유저 현황 등). 화면이 아직 없는 메뉴는 사이드바에 "준비 중" 으로 표시 | **ADMIN 전용** (일반 사용자는 `/home` 으로 리다이렉트) |

> 가계부 화면은 작성(`/wallet/new` → `/wallet/new/confirm` 2단계)과 조회/달력(`/wallet`)으로 나뉜다. `/wallet/analysis` 는 작성의 다음 단계가 아니라, 저녁 8시 이후 그날 지출을 판정하는 별도 흐름이다. 폴더는 각각 `pages/WalletEntry/`, `pages/Wallet/`. 공통 레이아웃(상단바·사이드바)은 `components/AppShell` 이 담당.

핵심 컴포넌트:
- `App.js` — `BrowserRouter` 안에 `<Routes>` 정의 + 사용자 state (`user`) 보유 + 첫 진입 시 `me()` 호출로 세션 자동 복원
- `ScrollToTop` — pathname 변경 시 스크롤 맨 위로 리셋
- `ProtectedRoute` — 인증 필요 경로(`/home`·`/wallet`·`/wallet/new*`·`/settings`) 가드. `authLoading` 동안 빈 화면 (깜빡임 방지), 미인증이면 `<Navigate to="/login" replace />`
- `AdminRoute` — `/admin/*` 가드. 미인증이면 `/login`, 로그인했지만 `role !== "ADMIN"` 이면 `/home` 으로 보냄

페이지 내부에서 다른 경로로 이동: `useNavigate()` 훅. `onNavigate` prop 패턴은 사용 안 함.

## 통신 규약

### 프론트 ↔ 백엔드

- 프로토콜: HTTP/1.1
- Content-Type: `application/json` (요청·응답 모두)
- 인증: 세션 쿠키 (`JSESSIONID`, HttpOnly)
- 모든 fetch 호출에 `credentials: 'include'` 필수
- CORS: 백엔드가 `localhost:3000` 만 허용 + `Access-Control-Allow-Credentials: true`

### 백엔드 ↔ MySQL

- 드라이버: `com.mysql.cj.jdbc.Driver`
- URL: `jdbc:mysql://127.0.0.1:3306/vori?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` (Windows IPv6 문제로 127.0.0.1 고정)
- 접속 정보: `application.properties` 에서 `${DB_USERNAME}` / `${DB_PASSWORD}` (`.env` 로부터)
- 커넥션 풀: HikariCP (Spring Boot 기본)

### 백엔드 ↔ 외부 API

- Gemini: REST/JSON, API key 헤더. `gemini.api.key` properties. 주 모델이 못 받으면 `gemini.fallback-models` 로 넘어간다
- 구글 로그인: 프론트가 받은 ID 토큰을 Google tokeninfo 로 검증(`GoogleTokenVerifier`)

## 데이터 흐름 큰 그림

### 회원 가입·로그인
```
회원가입 폼 → POST /api/auth/signup → users INSERT + user_stat_stats 4행 INSERT
           → 자동 login → JSESSIONID 발급 → /signup/profile (소비 프로필 설문)

로그인 → GET /api/onboarding/status
      → 설문 전이면 /signup/profile, 온보딩 전이면 /onboarding, 다 끝났으면 /home
      → 상태를 못 읽으면 이동하지 않고 로그인 화면에 "다시 시도" (상태만 다시 묻는다)
```

### 지출 기록 (구현됨)
```
작성 화면 → POST /api/ledger/entries (지출·수입·저축을 한 트랜잭션으로)
        → 지출마다 expenses INSERT (id 가 있으면 수정)
        → user_stat_stats EMA 갱신 (스탯 단위)
        → z_score 계산 → signal_initial 산정
        → signal_initial == RED 이고 반복 결제가 아니면 → AI 질문 (ai_inquiries INSERT)
        → 사용자 사유 응답 → reason_category 분류 → signal_final 보정
        → saved_amount > 0 면 → users.total_saved 누적 + goals.current_amount 누적
        → 하나라도 실패하면 위 전부 롤백

하루 판정 → POST /api/daily-judgments (일반 계정은 그날, 20시부터)
        → 스탯 그룹마다 예산 봉투로 판정(85% 이하 여유 · 100% 이하 적정 · 넘으면 초과), 그날 판정은 가장 나쁜 그룹
        → 답할 예외 지출이 있으면 1차 판정(PENDING) → 사유 답변 → POST /api/daily-judgments/finalize (FINALIZED)
        → 보상(남긴 돈으로 스탯·코인)은 그날 자정 정산에서 한 번 — docs/judgment-flow.md, docs/domain.md
```

### 영수증 OCR (구현됨)
```
영수증 사진 업로드 → receipt_ocr_jobs INSERT (status=PROCESSING)
                → Gemini 이미지 인식 (요청 안에서 동기 처리)
                → 추출 텍스트·금액·날짜·품목 저장 → status=SUCCESS|FAILED
                → 입력 화면을 자동으로 채움 → 사용자가 확인·수정 후 지출 저장
```

### 펫 성장·가챠·마이룸 (구현됨)
```
게임머니 충분 → eggs INSERT (purchased_at)
            → 사용자 개봉 클릭 → gacha_pulls INSERT (확률 분포 기반) + eggs.opened_at SET
            → pets INSERT (species_id, egg_id, hatched_at)
            → 소비 판정·상호작용·아이템 사용으로 스탯/레벨 성장
            → 마이룸에서 가구 구매·배치, 성장 완료 후 배웅
```

## 빌드·실행 환경

| 항목 | 값 |
|---|---|
| Java | 17 (Temurin 권장) |
| Node | 22 (`frontend/.nvmrc`, CI 도 같은 파일) |
| MySQL | 8.x 이상 (CI 는 8.4) |
| OS | Windows · macOS (개발), Linux (배포 예정) |
| 빌드 도구 | Gradle (백엔드), npm (프론트) |

## 보안 경계

- `.env` (시크릿) — gitignore 됨, 절대 커밋 X
- BCrypt 비번 해시 — 평문 저장·로그 노출 X
- CSRF: 현재 비활성 (REST + 세션 단순화). 운영 단계 진입 시 재검토
- HTTPS: 로컬 X. 운영 시 reverse proxy (nginx 등) 가 termination
- 어드민 계정 기본 비번 (`1234`) — 로컬 시연용. 배포 서버에서는 쓰지 않는다(관리자 비밀번호를 환경변수로 받는 변경이 #121)

## 관련 문서

- [`README.md`](../README.md) — 스택·셋업·구조·사용법
- [`DATABASE.md`](../DATABASE.md) — DB 셋업·Flyway·트러블슈팅
- [`docs/db-spec.md`](db-spec.md) — 테이블 명세 + 변경 이력
- [`docs/backend-flow.md`](backend-flow.md) — 요청·응답 흐름·레이어 책임
- [`docs/domain.md`](domain.md) — 비즈 룰·계산식·상태머신·용어집
