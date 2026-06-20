# Vori

> **합리성 판정 자기관리 앱** — 지출 + 맥락 + 사유를 받아 합리성 시그널(🟢/⚪/🔴) + 스탯 가감을 반환. 졸업작품.

## 스택

| 영역 | 기술 |
|------|------|
| 백엔드 | Java 17 + Spring Boot 3.x |
| 프론트엔드 | React.js (JavaScript) + HTML5 + CSS3 |
| DBMS | MySQL + MySQL Workbench |
| AI | Google Gemini API (REST/JSON) |
| IDE | IntelliJ IDEA · VS Code |
| 형상 관리 | GitHub |

## 구조

```
~/Projects/vori/
├── README.md
├── backend/    Spring Boot 3.5 + Java 17
└── frontend/   React 19 (Create React App)
```

## 첫 빌드 셋업

### 백엔드 (Spring Boot)
1. https://start.spring.io 에서 프로젝트 생성:
   - Project: Gradle - Groovy
   - Language: Java 17
   - Spring Boot: 3.x (최신 안정)
   - Group: `com.vori`, Artifact: `backend`
   - Dependencies: Spring Web, Spring Data JPA, MySQL Driver, Spring Security, Validation, Lombok, SpringDoc OpenAPI
2. 압축 풀어서 `~/Projects/vori/backend/` 로 이동
3. `./gradlew bootRun` 으로 실행 (port 8080)

> **실행 위치 주의**: `.env`(DB 비밀번호·Gemini 키)는 **repo 루트**에 있고 `spring.config.import` 가 CWD 상대경로라, 백엔드는 **repo 루트를 작업 디렉터리로** 실행해야 한다. `backend/` 에서 띄우면 `.env` 를 못 읽어 DB·AI 연결이 깨진다(로그인 실패 포함).
>
> **Windows 로그인 안 될 때**: DB URL 은 `127.0.0.1` 로 고정돼 있다(Windows 는 `localhost` 를 IPv6 `::1` 로 먼저 해석해 MySQL 연결이 실패할 수 있음). 그래도 안 되면 `.env` 를 **CRLF→LF** 로 저장하고, 본인 MySQL `root` 비밀번호를 `.env` 의 `DB_PASSWORD` 에 명시. 프론트는 `localhost:3000` / `127.0.0.1:3000` 둘 다 허용된다. 시드 계정은 `admin@vori.com` / `1234`.

### 프론트엔드 (React.js)
```bash
cd ~/Projects/vori
npx create-react-app frontend
cd frontend
npm start   # port 3000
```

### MySQL
```bash
brew install mysql              # macOS
brew services start mysql
mysql -u root -e "CREATE DATABASE vori;"
```

## 새 환경에서 실행 (클론 후)

repo 를 클론한 새 PC(팀원·다른 OS)에서 돌릴 때. **"환경 준비"는 사람이 1회, 그 다음은 백엔드가 자동.**

### 1회 수동 준비 (이게 안 되면 백엔드가 부팅 중 죽음 → 로그인 포함 전부 실패)
1. **MySQL 설치 + 실행**
   - macOS: `brew install mysql && brew services start mysql`
   - Windows: MySQL Installer 설치 후 MySQL 서비스 시작
2. **DB 생성**: `mysql -u root -p -e "CREATE DATABASE vori;"` (테이블이 아니라 DB 자체. Flyway 가 DB 는 안 만든다)
3. **`.env` 작성** — repo 루트에 `.env.example` 복사 후 값 채우기
   - `DB_USERNAME` / `DB_PASSWORD` = 본인 MySQL 계정 (Windows root 는 보통 비밀번호 있음 → 반드시 명시)
   - `GEMINI_API_KEY` = 본인 키
4. **백엔드 실행** — **repo 루트를 작업 디렉터리로** (`backend/` 에서 띄우면 `.env` 못 읽음)
5. **프론트엔드**: `cd frontend && npm install && npm start`

### 백엔드가 자동으로 하는 것 (위 준비가 끝났으면)
- MySQL 커넥션 풀 생성·연결
- Flyway 마이그레이션 자동 실행 → 테이블 생성/검증
- 시드 자동 생성 — 관리자 계정 `admin@vori.com` / `1234`

> Windows 에서 로그인 안 될 때 트러블슈팅은 위 [백엔드 셋업](#백엔드-spring-boot) 주의 박스 참조.

## 파일별 역할 가이드

---

### 🟦 backend/ (Spring Boot)

**패키징 컨벤션**: 기능 단위 (feature-based) — 한 도메인 폴더 안에 controller·service·entity·repository·dto·enum 을 함께 둔다. 한 기능 고치려면 한 폴더만 보면 됨.

#### 작업해야 하는 파일

| 파일 | 역할 | 할 일 |
|------|------|-------|
| `build.gradle` | 빌드·의존성 정의 | 새 라이브러리 추가 시 `dependencies { ... }`에 한 줄 |
| `src/main/resources/application.properties` | Spring 설정 | DB 연결·API 키 등 추가 |
| `src/main/resources/db/migration/V<N>__<설명>.sql` | Flyway 마이그레이션 | 스키마 변경 시 새 V 파일 추가 (V1 절대 수정 X) |
| `src/test/.../BackendApplicationTests.java` | 컨텍스트 로드 테스트 | 도메인 패키지 미러링해서 비즈 로직 테스트 추가 |

`application.properties` 주요 설정:
```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/vori?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
server.servlet.session.timeout=1h
gemini.api.key=${GEMINI_API_KEY}
```
> API 키·비밀번호는 `.env` 또는 환경변수로. 평문 커밋 금지.

#### 손 안 대는 파일

| 파일 | 역할 |
|------|------|
| `settings.gradle` | 루트 프로젝트 이름 (`backend`) |
| `gradlew` / `gradlew.bat` / `gradle/wrapper/` | Gradle Wrapper (팀원이 Gradle 설치 안 해도 빌드 가능) |
| `src/main/java/com/vori/backend/BackendApplication.java` | Spring Boot 진입점 (`main()` 하나) |
| `.gitignore` | `build/`·`.gradle/` 등 빌드 산출물 제외 |
| `.gitattributes` | 줄바꿈 (CRLF/LF) 통일 |

#### 도메인 패키지 (`src/main/java/com/vori/backend/`)

| 패키지 | 책임 | 포함되는 파일 |
|---|---|---|
| `auth/` | 인증·세션 | AuthController, UserPrincipal, CustomUserDetailsService, dto/ |
| `user/` | 사용자 도메인 | User(Entity), Role(enum), UserRepository, UserService |
| `common/` | 도메인 공용 | StatType 등 여러 도메인이 공유하는 enum |
| `budget/` | 월 예산 | MonthlyBudget + Repository |
| `category/` | 지출 카테고리 | Category + Repository |
| `expense/` | 지출 | Expense, Signal(enum), Repository |
| `income/` | 수입 | Income, IncomeSource(enum), Repository |
| `goal/` | 절약 목표 | Goal, GoalStatus(enum), Repository |
| `stats/` | EMA 통계 | UserStatStats, UserStatStatsId(composite PK), Repository |
| `inquiry/` | AI 사유 질문 | AiInquiry, ReasonCategory(enum), Repository |
| `receipt/` | 영수증 OCR | ReceiptOcrJob, OcrProvider/OcrStatus(enum), Repository |
| `report/` | 일일 리포트 | DailyReport + Repository |
| `pet/` | 펫·알·가챠·성장 | Pet, PetSpecies, Egg, GachaPull, PetGrowthLog + 4 enum + 5 Repository |
| `theme/` | 테마 마스터 | ThemeMaster + Repository |
| `furniture/` | 사용자 가구 | UserFurniture, FurnitureCategory(enum), Repository |
| `title/` | 사용자 칭호 | UserTitle + Repository |
| `config/` | 환경 설정 | SecurityConfig (FilterChain, CORS, PasswordEncoder) |
| `seeder/` | 초기 데이터 시더 | AdminSeeder, CategorySeeder, PetSpeciesSeeder |

#### 각 도메인 안 파일 패턴

```
<도메인>/
├── <Entity>.java            JPA @Entity (테이블 매핑)
├── <Entity>Repository.java  DAO (extends JpaRepository<T, ID>)
├── <Entity>Service.java     비즈 로직 (필요한 경우)
├── <Entity>Controller.java  REST API (필요한 경우)
├── <Enum>.java              해당 도메인 전용 enum (있는 경우)
└── dto/                     요청/응답 DTO (외부 노출 도메인만)
```

#### 새 기능 추가 시 예시

지출 등록 기능을 만든다면 `expense/` 폴더에 다음을 추가:
- `ExpenseController.java` — `POST /api/expenses` 등 REST 엔드포인트
- `ExpenseService.java` — 비즈 로직 (z-score 산출·신호등 판정 등)
- `dto/ExpenseCreateRequest.java`, `dto/ExpenseResponse.java`
- `Expense.java` 와 `ExpenseRepository.java` 는 이미 있음 (재사용)

---

### 🟩 frontend/ (React)

**라우팅**: React Router v7 의 `BrowserRouter` 사용. 페이지 전환마다 URL 이 바뀌며 브라우저 뒤로가기·새로고침·링크 공유 모두 동작.

| 경로 | 페이지 | 인증 |
|---|---|---|
| `/` | LandingPage | 공개 |
| `/login` | LoginPage | 공개 |
| `/signup` | SignupPage | 공개 |
| `/story` | StoryPage | 공개 |
| `/home` | HomeDashboard | **인증 필요** (미인증 시 `/login` 으로 리다이렉트) |

`App.js` 가 `BrowserRouter` 안에 `<Routes>` 정의 + 사용자 인증 state(`user`) 보유 + 첫 진입 시 `me()` 호출로 세션 복원. `ProtectedRoute` 컴포넌트가 `/home` 가드.

페이지 안에서 다른 페이지로 이동할 땐 `useNavigate()` 훅 사용:

```jsx
import { useNavigate } from "react-router-dom";
const navigate = useNavigate();
navigate("/login");
```

#### 작업해야 하는 파일

| 파일 | 역할 | 할 일 |
|------|------|-------|
| `package.json` | npm 의존성·스크립트 | 라이브러리 추가 시 `npm install <name>` (예: `axios`) |
| `public/index.html` | HTML 템플릿 | `<title>VORI</title>`로 변경 정도 |
| `src/index.js` | React 진입점 (`<App />` mount) | 거의 손댈 일 없음 |
| `src/index.css` | 전역 스타일 | 디자인 시스템(색·폰트) 정의 |
| `src/App.js` | 최상위 컴포넌트 | `BrowserRouter` + `<Routes>` 정의. 새 페이지 추가 시 `<Route>` 한 줄 추가 |
| `src/App.css` | App 컴포넌트 스타일 | 갈아엎기 |
| `src/App.test.js` | App 테스트 1개 (현재 깨짐) | 의미 있는 테스트로 교체 또는 삭제 |

#### 손 안 대는 / 자동 생성

| 파일 | 역할 |
|------|------|
| `package-lock.json` | npm 잠금 파일 (**수동 편집 금지**, 자동 생성) |
| `src/reportWebVitals.js` · `src/setupTests.js` | CRA 기본 유틸 |

#### 디자인 나오면 교체

| 파일 | 비고 |
|------|------|
| `public/favicon.ico` · `logo192.png` · `logo512.png` · `manifest.json` · `robots.txt` | 정적 자산 |
| `src/logo.svg` | React 기본 로고 |

#### 새로 만들 폴더 (`src/` 아래)

| 폴더 | 역할 | 예시 |
|------|------|------|
| `pages/` | 페이지 단위 컴포넌트 | `Home`, `Wallet`(가계부 조회 + 소비 분석 이벤트), `WalletEntry`(작성 2-step), `Admin`, `Settings` |
| `components/` | 재사용 컴포넌트 | `AppShell`(상단바+사이드바 셸), `AppRightSidebar` |
| `api/` | 백엔드 호출 함수 | axios wrapper |
| `hooks/` | 커스텀 훅 | `useAuth`, `useExpense` |
| `styles/` | 공통 CSS | 변수·믹스인 |

---

## 관련 문서

| 문서 | 내용 |
|---|---|
| [`DATABASE.md`](DATABASE.md) | DB 셋업·Flyway·application.properties·트러블슈팅 |
| [`docs/architecture.md`](docs/architecture.md) | 시스템 경계·외부 의존·통신 규약·큰 그림 데이터 흐름 |
| [`docs/backend-flow.md`](docs/backend-flow.md) | 요청·응답 흐름·레이어 책임·신규 기능 추가 체크리스트 |
| [`docs/domain.md`](docs/domain.md) | 비즈 룰·계산식·상태머신·용어집 (Service 짤 때 SSOT) |
| [`docs/db-spec.md`](docs/db-spec.md) | 테이블 18종 명세 + 변경 이력 |
