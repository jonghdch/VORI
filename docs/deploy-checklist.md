# 배포 점검표 — AWS EC2 + Nginx (초안)

> 작성 2026-10-16 · 황채린 · 상태: **초안 — 배포 담당(정다은)이 실제 서버에 맞게 고친다**
>
> 코드에서 배포 때 걸리는 설정을 모아 순서대로 적었다. 설정 파일 초안은 [`deploy/`](../deploy/)에 있다.
> 경진대회(11/11)에서 관람객이 휴대폰으로 QR 을 찍고 직접 가입해 쓰는 것을 기준으로 한다.

## 한눈에

```
휴대폰 ─ https://도메인 ─ Nginx ┬─ /        → /var/www/vori (프론트 빌드)
                                └─ /api/   → 127.0.0.1:8080 (Spring Boot) ─ MySQL(같은 서버)
```

| 파일 | 놓을 곳 |
|---|---|
| [`deploy/nginx/vori.conf`](../deploy/nginx/vori.conf) | `/etc/nginx/conf.d/vori.conf` |
| [`deploy/vori-backend.service`](../deploy/vori-backend.service) | `/etc/systemd/system/vori-backend.service` |
| [`deploy/server.env.example`](../deploy/server.env.example) | 채워서 `/opt/vori/.env` (권한 600) |

## 1. 서버 준비

- [ ] EC2 인스턴스 — Spring Boot + MySQL 을 한 대에 두면 메모리 1GB 는 빠듯하다(추정). 2GB 이상 권장, 실측해 볼 것
- [ ] 보안 그룹: **22(내 IP 만), 80, 443** 만 연다. 8080·3306 은 열지 않는다
- [ ] Java 17, MySQL 8.4(CI 와 같은 버전), Nginx, certbot 설치
- [ ] 서버 시간대는 신경 쓰지 않아도 된다 — 코드가 Asia/Seoul 로 고정한다(`BackendApplication`, DB 접속 `serverTimezone`, 자정 정산 cron)

## 2. DB

- [ ] DB `vori` (utf8mb4) 와 전용 사용자(`vori`) 만들기 — root 로 접속하지 않는다
- [ ] 테이블은 만들지 않는다 — 백엔드 첫 실행 때 Flyway 가 V1 부터 전부 적용한다(`ddl-auto=validate`)
- [ ] 접속 주소는 코드에 `127.0.0.1:3306/vori` 고정. 다른 곳이면 `.env` 에 `spring.datasource.url` 을 쓴다

## 3. 백엔드

- [ ] `backend` 에서 `./gradlew bootJar` → `build/libs/backend-0.0.1-SNAPSHOT.jar`(`-plain` 아닌 것)를 `/opt/vori/vori-backend.jar` 로
- [ ] `/opt/vori/.env` 채우기([`server.env.example`](../deploy/server.env.example))
  - `DB_USERNAME`·`DB_PASSWORD`
  - **`ADMIN_PASSWORD`** — 첫 실행에서만 관리자(admin@vori.com)를 만든다. 비우면 안 만든다. 저장소가 공개라 1234 금지(#121)
  - `GEMINI_API_KEY` — 유료 결제를 켠 프로젝트의 키
  - `GOOGLE_CLIENT_ID`
  - `vori.ai-judge.open-hour` — 평소 20, 낮 발표·경진대회 기간은 0
  - `server.address=127.0.0.1` — Nginx 만 8080 에 닿게
- [ ] systemd 로 띄우기 — **`WorkingDirectory` 가 `.env` 가 있는 폴더여야 한다**. 아니면 `Access denied ... (using password: NO)`
- [ ] prod 프로필(`-Dspring.profiles.active=prod`) — SQL 로그를 끈다(`application-prod.properties`, #121)
- [ ] 확인: 서버에서 `curl -i http://127.0.0.1:8080/api/auth/me` → **401** 이면 정상

## 4. 프론트

- [ ] `frontend` 에서 빌드할 때 넣을 값(빌드 때 박힌다 — 바꾸면 다시 빌드)
  - `REACT_APP_GOOGLE_CLIENT_ID` = 서버의 `GOOGLE_CLIENT_ID` 와 같은 값
  - `REACT_APP_AI_ACTIVE_FROM_HOUR` = 서버의 `vori.ai-judge.open-hour` 와 같은 값
  - `REACT_APP_API_BASE` 는 **넣지 않는다** → `/api` 로 부른다(#122)
- [ ] `npm ci && npm run build` → `build/` 를 `/var/www/vori/` 로

## 5. Nginx · HTTPS

- [ ] 도메인 연결(구글 로그인은 IP 주소로 안 된다 — 도메인 + HTTPS 필요)
- [ ] certbot 으로 인증서 → [`vori.conf`](../deploy/nginx/vori.conf) 의 도메인·인증서 경로 바꾸기
- [ ] `nginx -t` 후 다시 읽기
- [ ] 이 설정에 꼭 있어야 하는 것
  - `try_files $uri $uri/ /index.html` — 없으면 `/home` 새로고침이 404
  - `client_max_body_size 11m` — 기본 1MB 면 영수증 사진이 413
  - `proxy_read_timeout 420s` — 기본 60초면 AI 응답(최악 수 분) 전에 504
- [ ] HTTPS 가 된 뒤 `.env` 에 `server.servlet.session.cookie.secure=true`, `server.forward-headers-strategy=native` 켜고 백엔드 다시 시작

## 6. 구글 로그인

- [ ] Google Cloud 콘솔 > 사용자 인증 정보 > 그 OAuth 클라이언트 > **「승인된 JavaScript 원본」에 `https://도메인`**
  - 구글 버튼(ID 토큰 방식)은 리디렉션 URI 가 아니라 JavaScript 원본을 본다
- [ ] OAuth 동의 화면의 게시 상태 확인 — 「테스트」면 등록한 테스트 사용자만 로그인될 수 있다. 관람객이 쓰려면 「프로덕션」으로(콘솔에서 확인 필요)

## 7. 확인 (휴대폰으로)

- [ ] `https://도메인` 첫 화면, 아무 화면에서 새로고침
- [ ] 가입 → 설문 → 홈 (펫 이름·출석 팝업 순서)
- [ ] 지출 기록 → 카테고리 자동 분류
- [ ] 영수증 사진(큰 사진) 올리기 — 413·504 가 나지 않는지
- [ ] 판정하기 → 사유 답변 → 최종 판정(판정 시각 설정 확인)
- [ ] 구글 로그인
- [ ] 관리자 로그인 → AI 사용량 화면(Gemini 한도·스위치)
- [ ] 시연 계정: 서버에서는 `VORI_BASE_URL`·`VORI_ADMIN_PASSWORD`·`VORI_DEMO_PASSWORD` 를 주고 `scripts/demo_seed.py`(#126 — 기본 비밀번호면 멈춘다)

## 8. 운영

- [ ] DB 백업: 매일 `mysqldump` (cron), **경진대회 전날 따로 한 번**
- [ ] 로그: `journalctl -u vori-backend -f`
- [ ] 다시 배포: 새 jar 복사 → `systemctl restart vori-backend` / 새 `build/` 복사(Nginx 재시작 필요 없음)
- [ ] 되돌리기: 이전 jar·build 를 하나씩 남겨 둔다. **이미 머지된 Flyway 마이그레이션 파일은 고치지 않는다**(체크섬이 깨져 서버가 안 뜬다)

## 경진대회 날

- [ ] 전날 밤: 관리자 → AI 사용량에서 **일일 코멘트 AI 끄기**(0시 10분 배치가 한도를 쓴다, final-demo-plan 4절)
- [ ] 판정 시각 0(서버·프론트 둘 다)
- [ ] `PET_CHAT_DAILY_LIMIT` 낮추기(게스트가 많으면)
- [ ] QR 이 가리키는 주소가 https 인지
