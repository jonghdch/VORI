# Vori

> **합리성 판정 자기관리 앱** — 지출 + 맥락 + 사유를 받아 4 방식 알고리즘으로 합리성 시그널(🟢/⚪/🔴) + 스탯 가감을 반환. 졸업작품.

## 스택 (회의 확정)

| 영역 | 기술 |
|------|------|
| 백엔드 | Java 17 + Spring Boot 3.x |
| 프론트엔드 | React.js (JavaScript) + HTML5 + CSS3 |
| DBMS | MySQL + MySQL Workbench |
| AI | OpenAI GPT-4o-mini API (REST/JSON) |
| IDE | IntelliJ IDEA · VS Code |
| 형상 관리 | GitHub |

## 구조

```
~/Projects/vori/
├── README.md
├── docs/                    팀 공유 문서
│   ├── system-design.md     IV. 시스템 설계
│   ├── ui-ux-design.md      UI/UX 설계
│   └── feature-member.md    회원 기능 모듈 설계
├── backend/                 (회의 후 셋업) Spring Boot
├── frontend/                (회의 후 셋업) React
└── prototype-python/        Python 프로토타입 (Java 재작성용 참조)
    └── README.md
```

## 첫 빌드 셋업 (회의 후)

### 백엔드 (Spring Boot)
1. https://start.spring.io 에서 프로젝트 생성:
   - Project: Gradle - Groovy 또는 Kotlin
   - Language: Java
   - Spring Boot: 3.x (최신 안정)
   - Group: `com.vori`, Artifact: `backend`
   - Dependencies: Spring Web, Spring Data JPA, MySQL Driver, Spring Security, Validation, Lombok, SpringDoc OpenAPI
2. 압축 풀어서 `~/Projects/vori/backend/` 로 이동
3. `./gradlew bootRun` 으로 실행 (port 8080)

### 프론트엔드 (React)
```bash
cd ~/Projects/vori
npm create vite@latest frontend -- --template react
cd frontend
npm install
npm run dev   # port 5173
```

### MySQL
```bash
brew install mysql              # macOS
brew services start mysql
mysql -u root -e "CREATE DATABASE vori;"
```

## 4 방식 알고리즘 (회의 결정 사항)

| 키 | 방식 | 상태 |
|---|------|------|
| A | OpenAI GPT-4o-mini 직접 판정 | 본 빌드 |
| B | 한국어 임베딩 + k-NN | **회의 결정 필요** (DJL / Python 마이크로서비스 / OpenAI Embedding / 폐기) |
| C | 규칙 엔진 8개 | 본 빌드 (Python prototype 참조) |
| D | 하이브리드 (C 우선 → B 폴백) | 본 빌드 |

## 팀 작업 흐름

1. 본인 브랜치 생성: `git checkout -b feature/<기능명>`
2. 작업 + 커밋 + push: `git push origin feature/<기능명>`
3. GitHub 에서 Pull Request 열기
4. 리뷰어가 검수 후 main 으로 머지

## 프로토타입 보존

`prototype-python/` 에 Python 프로토타입 보존 (운영 X). 4 방식 알고리즘 로직과 라벨 DB 22건이 Java 재작성 시 참조 자산.

## 졸작 정보

- **시연 대상**: 20명 (졸작 시연)
- **확장 대상**: 친구·지인 100명 (졸작 후)
- **출시 일정**: 졸작 일정 (학기 단위, 3~4개월)
