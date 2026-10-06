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
┌──────────────┐  ┌─────────────────┐  ┌────────────────────┐
│ MySQL 9.x    │  │ Google Gemini   │  │ Google Vision OCR  │
│ :3306        │  │ (AI 사유 질문)   │  │ (영수증 텍스트)     │
│ DB: vori     │  │  미연결          │  │  미연결            │
└──────────────┘  └─────────────────┘  └────────────────────┘
```

## 컴포넌트별 책임

| 컴포넌트 | 책임 | 상태 |
|---|---|---|
| Frontend (React) | 화면·폼·라우팅·세션 쿠키 전송 | 진행 중 (랜딩·로그인·회원가입·스토리·홈·가계부(작성/조회)·환경설정·어드민 페이지) |
| Backend (Spring Boot) | REST API·인증·비즈 로직·DB 접근 | 진행 중 (auth·expense·income·savings·category·stats·inquiry·admin 컨트롤러/서비스 구현. furniture·pet·goal·budget·report·title·theme·receipt 는 Entity·Repository 중심) |
| MySQL | 영속 데이터 저장 | 18개 테이블, V1__init.sql 자동 적용 |
| Google Gemini | AI 사유 질문 생성·답변 분류·카테고리 임베딩 | **연결됨** (`gemini.api.key` = `.env` 의 `GEMINI_API_KEY`. 키 없으면 호출 시 403) |
| Google Vision | 영수증 OCR | **미연결** (Phase 2) |

## 포트·URL

| 서비스 | 로컬 URL |
|---|---|
| 프론트엔드 | http://localhost:3000 |
| 백엔드 API | http://localhost:8080/api |
| MySQL | localhost:3306 (DB 이름 `vori`) |

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
| `/wallet/analysis` | WalletAnalysisPage (AI 사유 질문·일일 판정) | **인증 필요** |
| `/wallet/new/confirm` | WalletConfirmPage (Step 3 · 확인) | **인증 필요** |
| `/report` | ReportPage (주·월 소비 리포트) | **인증 필요** |
| `/myroom` | PetPage (마이룸·펫 성장·가구 배치) | **인증 필요** |
| `/dex`, `/dex/:appearanceKey` | 펫·업적·칭호 도감 | **인증 필요** |
| `/shop` | ShopPage (알·가구 상점) | **인증 필요** |
| `/settings/:tab` | SettingsPage (프로필·기본 설정) | **인증 필요** |
| `/admin` (→ `/admin/dashboard`) | AdminLayout + 중첩 라우트 (종합 대시보드·유저 현황 등) | **ADMIN 전용** (일반 사용자는 `/home` 으로 리다이렉트) |

> 가계부 화면은 작성(`/wallet/new` 3-step)과 조회/달력(`/wallet`)으로 나뉜다. 폴더는 각각 `pages/WalletEntry/`, `pages/Wallet/`. 공통 레이아웃(상단바·사이드바)은 `components/AppShell` 이 담당.

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
- URL: `jdbc:mysql://localhost:3306/vori?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`
- 접속 정보: `application.properties` 에서 `${DB_USERNAME}` / `${DB_PASSWORD}` (`.env` 로부터)
- 커넥션 풀: HikariCP (Spring Boot 기본)

### 백엔드 ↔ 외부 API (Phase 2)

- Gemini: REST/JSON, API key 헤더. `gemini.api.key` properties
- Vision: REST/JSON, GCP credentials

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
        → 그날 지출 중 가장 강한 신호로 판정 → 코인 + 펫 4개 스탯 지급 + pet_growth_logs INSERT
```

### 영수증 OCR (구현됨)
```
영수증 사진 업로드 → receipt_ocr_jobs INSERT (status=PENDING)
                → Gemini 이미지 인식 → status=PROCESSING
                → 추출 텍스트·금액·날짜·품목 저장 → status=SUCCESS|FAILED
                → 입력 화면을 자동으로 채움 → 사용자가 확인·수정 후 지출 저장
```

### 펫 성장·가챠·마이룸 (구현됨)
```
게임머니 충분 → eggs INSERT (purchased_at)
            → 사용자 개봉 클릭 → gacha_pulls INSERT (확률 분포 기반) + eggs.opened_at SET
            → pets INSERT (species_id, egg_id, hatched_at)
            → 소비 판정·상호작용·아이템 사용으로 스탯/레벨 성장
            → 마이룸에서 가구 구매·배치, 성장 완료 후 분양
```

## 빌드·실행 환경

| 항목 | 값 |
|---|---|
| Java | 17 (Temurin 권장) |
| Node | (CRA 호환 버전, 18+ 권장) |
| MySQL | 8.x 또는 9.x |
| OS | macOS / Linux (개발 기준) |
| 빌드 도구 | Gradle (백엔드), npm (프론트) |

## 보안 경계

- `.env` (시크릿) — gitignore 됨, 절대 커밋 X
- BCrypt 비번 해시 — 평문 저장·로그 노출 X
- CSRF: 현재 비활성 (REST + 세션 단순화). 운영 단계 진입 시 재검토
- HTTPS: 로컬 X. 운영 시 reverse proxy (nginx 등) 가 termination
- 어드민 계정 평문 비번 (`1234`) — 시연·평가 단계만. 운영 진입 시 강한 값으로 교체 + 시더 제거

## 관련 문서

- [`README.md`](../README.md) — 스택·셋업·구조·사용법
- [`DATABASE.md`](../DATABASE.md) — DB 셋업·Flyway·트러블슈팅
- [`docs/db-spec.md`](db-spec.md) — 테이블 18종 명세 + 변경 이력
- [`docs/backend-flow.md`](backend-flow.md) — 요청·응답 흐름·레이어 책임
- [`docs/domain.md`](domain.md) — 비즈 룰·계산식·상태머신·용어집
