# Vori

> **합리성 판정 자기관리 앱** — 지출 + 맥락 + 사유를 받아 4 방식 알고리즘으로 합리성 시그널(🟢/⚪/🔴) + 스탯 가감을 반환. 졸업작품.

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

## 팀 작업 흐름

1. 본인 브랜치 생성: `git checkout -b feature/<기능명>`
2. 작업 + 커밋 + push: `git push origin feature/<기능명>`
3. GitHub 에서 Pull Request 열기
4. 리뷰어가 검수 후 main 으로 머지

## 파일별 역할 가이드

---

### 🟦 backend/ (Spring Boot)

#### 작업해야 하는 파일

| 파일 | 역할 | 할 일 |
|------|------|-------|
| `build.gradle` | 빌드·의존성 정의 | 새 라이브러리 추가 시 `dependencies { ... }`에 한 줄 (예: Gemini client, jwt) |
| `src/main/resources/application.properties` | Spring 설정 | DB 연결·API 키 등 추가 (아래 예시) |
| `src/test/.../BackendApplicationTests.java` | 컨텍스트 로드 테스트 | `controller/`·`service/` 미러링해서 비즈 로직 테스트 추가 |

`application.properties` 추가 예시:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/vori
spring.datasource.username=root
spring.datasource.password=...
spring.jpa.hibernate.ddl-auto=update
gemini.api.key=${GEMINI_API_KEY}
```
> API 키는 `.env` 또는 환경변수로. 평문 커밋 금지.

#### 손 안 대는 파일

| 파일 | 역할 |
|------|------|
| `settings.gradle` | 루트 프로젝트 이름 (`backend`) |
| `gradlew` / `gradlew.bat` / `gradle/wrapper/` | Gradle Wrapper (팀원이 Gradle 설치 안 해도 빌드 가능) |
| `src/main/java/com/vori/backend/BackendApplication.java` | Spring Boot 진입점 (`main()` 하나) |
| `.gitignore` | `build/`·`.gradle/` 등 빌드 산출물 제외 |
| `.gitattributes` | 줄바꿈 (CRLF/LF) 통일 |

#### 새로 만들 폴더 (`src/main/java/com/vori/backend/` 아래)

| 폴더 | 역할 | 예시 |
|------|------|------|
| `controller/` | REST API 엔드포인트 | `@RestController` 클래스 |
| `service/` | 비즈 로직 | 4 방식 알고리즘 A/B/C/D |
| `repository/` | DB 접근 | `extends JpaRepository` |
| `entity/` | JPA 엔티티 | `@Entity` (User·Expense·Stat 등) |
| `dto/` | 요청/응답 객체 | `LoginRequest`, `SignalResponse` 등 |
| `config/` | 환경 설정 | Spring Security·CORS |

---

### 🟩 frontend/ (React)

#### 작업해야 하는 파일

| 파일 | 역할 | 할 일 |
|------|------|-------|
| `package.json` | npm 의존성·스크립트 | 라이브러리 추가 시 `npm install <name>` (예: `axios`, `react-router-dom`) |
| `public/index.html` | HTML 템플릿 | `<title>VORI</title>`로 변경 정도 |
| `src/index.js` | React 진입점 (`<App />` mount) | 라우터 도입 시 `<BrowserRouter>` 감싸기 |
| `src/index.css` | 전역 스타일 | 디자인 시스템(색·폰트) 정의 |
| `src/App.js` | 최상위 컴포넌트 (현재 CRA 기본 화면) | **통째로 갈아엎기** — 페이지 라우팅·레이아웃 |
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
| `pages/` | 페이지 단위 컴포넌트 | `Login`, `Dashboard`, `ExpenseInput` |
| `components/` | 재사용 컴포넌트 | `Button`, `Card`, `SignalBadge` |
| `api/` | 백엔드 호출 함수 | axios wrapper |
| `hooks/` | 커스텀 훅 | `useAuth`, `useExpense` |
| `styles/` | 공통 CSS | 변수·믹스인 |
