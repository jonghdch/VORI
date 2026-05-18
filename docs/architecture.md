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
| Frontend (React) | 화면·폼·라우팅·세션 쿠키 전송 | 진행 중 (랜딩/로그인/회원가입/스토리 페이지) |
| Backend (Spring Boot) | REST API·인증·비즈 로직·DB 접근 | 진행 중 (auth 모듈만 구현. 나머지 도메인은 Entity·Repository 만) |
| MySQL | 영속 데이터 저장 | 18개 테이블, V1__init.sql 자동 적용 |
| Google Gemini | AI 사유 질문 생성·답변 분류 | **미연결** (Phase 2) |
| Google Vision | 영수증 OCR | **미연결** (Phase 2) |

## 포트·URL

| 서비스 | 로컬 URL |
|---|---|
| 프론트엔드 | http://localhost:3000 |
| 백엔드 API | http://localhost:8080/api |
| MySQL | localhost:3306 (DB 이름 `vori`) |

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
           → 자동 login → JSESSIONID 발급 → 랜딩 진입
```

### 지출 기록 (Phase 2 — 미구현)
```
지출 입력 → expenses INSERT
        → user_stat_stats EMA 갱신 (스탯 단위)
        → z_score 계산 → signal_initial 산정
        → signal_initial ∈ {RED, GRAY} 면 → AI 질문 (ai_inquiries INSERT)
        → 사용자 사유 응답 → reason_category 분류 → signal_final 보정
        → saved_amount > 0 면 → users.total_saved 누적 + goals.current_amount 누적
        → stat_delta = floor(saved_amount/1000) → pets.stat_<type> 증가 + pet_growth_logs INSERT
```

### 영수증 OCR (Phase 2 — 미구현)
```
영수증 사진 업로드 → receipt_ocr_jobs INSERT (status=PENDING)
                → 비동기 Vision API 호출 → status=PROCESSING
                → 추출 텍스트·금액·날짜·품목 저장 → status=SUCCESS|FAILED
                → 사용자가 확인 후 expense_id 매칭 시 expenses INSERT
```

### 펫 성장·가챠 (Phase 2 — 미구현)
```
게임머니 충분 → eggs INSERT (purchased_at)
            → 사용자 개봉 클릭 → gacha_pulls INSERT (확률 분포 기반) + eggs.opened_at SET
            → pets INSERT (species_id, egg_id, hatched_at)
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
