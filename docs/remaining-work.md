# VORI 남은 작업 — 일관성·정합성을 지키며 완성하기

> 작성 2026-11-02 · 근거는 [`AUDIT-2026-11-02.md`](AUDIT-2026-11-02.md). 우선순위는 **경진대회 2026-11-11** 기준.
> 규모: S = 1시간 이내, M = 반나절, L = 하루 이상. 항목을 끝내면 체크하고 근거 문서(domain.md·db-spec.md·deploy 문서)를 같은 PR에서 갱신한다.

## 현재 상태

`https://vori.worldpick.co.kr`에 도커로 배포돼 서비스 중이다(커밋 `3481a4a`, 로컬·GitHub·운영 동일). 백엔드 테스트 343건 통과, 프론트 빌드·ESLint 경고 0, Flyway 55개 적용, 인증서·HTTPS·세션 쿠키·로그인 제한·IDOR 방어가 정상이다. 다만 `GEMINI_API_KEY`·`GOOGLE_CLIENT_ID`가 비어 있어 **AI 기능(카테고리 분류·질문·펫 대화·영수증)과 구글 로그인은 아직 동작하지 않고**, DB 백업과 모니터링이 없다.

## 0. 지금 당장 (운영 차단 요소)

- [ ] `GEMINI_API_KEY`·`GOOGLE_CLIENT_ID` 입력 → `ssh zzemsoft 'cd /data/home2/vori.worldpick.co.kr && runuser -u vori -- ./deploy.sh'` — `VORI/.env`, 로그 `CategorizeService 카테고리 임베딩 초기화 실패` — S
- [ ] Google 콘솔 OAuth 클라이언트 '승인된 JavaScript 원본'에 `https://vori.worldpick.co.kr` 추가, 동의 화면이 「테스트」면 「프로덕션」 — `docs/deploy-checklist.md:69-71` — S
- [ ] DB 백업 cron — `/etc/cron.d/vori-backup` 03:25, `docker exec vori-mysql mysqldump --single-transaction vori | gzip` → `/data/backup/vori/vori_YYYY-MM-DD.sql.gz`, 14일 보존, 복원 리허설 1회(임시 컨테이너에 복원해 `SELECT COUNT(*) FROM users`) — `/home/dockers/backup.sh:26`에 vori 없음 — M
- [ ] 시연 시각 open-hour=0 — 서버 `VORI/.env` `vori.ai-judge.open-hour=0` 변경 후 `deploy.sh`(프론트 `REACT_APP_AI_ACTIVE_FROM_HOUR` 재빌드 자동 감지). 시연 후 20으로 되돌리기(`close-hour` 는 건드리지 않는다 — `open-hour=0` 이면 새벽 연장이 없다) — `src/config.js:15-20` — S
- [ ] 판정 알림 시각을 설정값과 연동 — `JudgmentOpenNotifier.java:30` cron이 20시 고정이라 open-hour=0이어도 알림은 20시. `@Scheduled`를 설정 기반으로 바꾸거나 시연 기간엔 알림 문구를 "지금 판정 가능"으로 — M
- [ ] 업타임 모니터링 — 외부 체크(`GET /api/auth/me` → 401 정상, `GET /` → 200), 실패 시 알림. 서버에 체계 없음 — S
- [ ] GitHub Actions 자동 배포 — 2026-11-03 구성(secret 등록 대기). 서버: vori 계정 배포 키(forced command `deploy-ssh-entry.sh` → `deploy.sh`, 접속 시험 통과), 레포: `.github/workflows/deploy.yml`(CI 성공 `workflow_run` + 수동 `workflow_dispatch`). 남은 일: 저장소 Settings → Secrets and variables → Actions 에 `DEPLOY_SSH_KEY`·`DEPLOY_HOST`·`DEPLOY_PORT`·`DEPLOY_KNOWN_HOSTS` 등록(jonghdch 권한 필요) → Actions 에서 `Deploy` 수동 실행 1회로 확인 — `docs/deploy-checklist.md` 2-3절 — S
- [x] (2026-11-03 반영) 운영 권한 전환 — `/data/home2/vori.worldpick.co.kr` 전부 vori 소유, deploy.sh 는 vori 로 실행(root 거부), vori-backend·vori-mysql 컨테이너 `user: 1027:1027`, vori 는 docker 그룹(root 동급 권한임에 주의). 전환 직전 DB 백업 `.deploy-state/pre-chown-backup-20261103-001916.sql.gz` — `docs/deploy-checklist.md` 권한 원칙 — M
- [ ] `VORI/.env` 의 `GOOGLE_CLIENT_ID` 값 확인 — 2026-11-03 현재 OAuth **클라이언트 ID 가 아니라 클라이언트 보안 비밀(GOCSPX- 로 시작)** 이 들어가 있고, deploy.sh 가 이를 `REACT_APP_GOOGLE_CLIENT_ID` 로 프론트 번들에 넣어 공개 JS 에 노출돼 있다. Google 콘솔에서 보안 비밀 재발급(폐기) → `.env` 에 `…apps.googleusercontent.com` 형식 클라이언트 ID 입력 → `deploy.sh`(프론트 자동 재빌드) — S

## 1. 경진대회 전 (11/11) 필수 — 관람객 노출 결함

- [ ] 지출 0건인 날 판정 보상 차단 — `DailyJudgmentService.java:44-63` judgeDate에서 `expenseCount==0`이면 403 "오늘 지출 기록이 없어요" 또는 보상 0. `judgment-flow.md:119,166`의 자정 규칙과 통일 — S
- [ ] `path="*"` 라우트 — `App.js:305-405`, 안내 화면 또는 `/`로 — S
- [ ] 로그인 화면 미동작 컨트롤 2개 제거 — `LoginPage.jsx:140-150` 「로그인 상태 유지」·「비밀번호 찾기」 — S
- [ ] 네트워크 오류 한글화 — `api/http.js:50` `request()`/`upload()`에서 fetch TypeError를 "인터넷 연결을 확인해 주세요"로 — S
- [x] (2026-11-03 디자인 통일에서 반영) iOS 입력 16px — 모든 입력 필드를 `--fs-input`(16px)으로. 설정·프로필 입력처럼 크기를 안 정해 13px 를 물려받던 곳도 포함 — S
- [ ] 숫자 키패드 — `PaymentSettingsPanel.jsx:166,222`, `GeneralSettingsPanel.jsx:118`, `ProfileSettingsPanel.jsx:97,107` `inputMode="numeric"` — S
- [ ] 관리자 비밀번호가 1234가 아닌지 확인 — 운영 `VORI/.env` `ADMIN_PASSWORD`(배포 시 무작위 생성), `scripts/categorize_eval.py` 기본값 1234 제거 — S
- [ ] 가입 IP 제한 — `AuthController.java:45-50` 가입마다 1,000코인·시작 펫, 이메일 인증 없음. `LoginAttemptLimiter` 패턴으로 IP당 시간당 N회 — M
- [ ] Gemini 질문 생성 1인 일일 상한 — `ExpenseService.java:126-131`, 초과 시 템플릿 문구. 한 사람이 전체 한도를 소진하지 못하게 — M
- [ ] 영수증 FAILED를 일일 한도에서 제외 — `ReceiptService.java:71-72,172-178` — S
- [ ] `scripts/demo_seed.py` 운영 실행(시연 계정) + `scripts/categorize_eval.py` 1회 실행, 결과를 `docs/categorize-eval-YYYY-MM-DD.md`에 기록 — `final-demo-plan.md` — M
- [ ] 펫 PNG 최적화(선택) — `src/assets/pets/*.png` 33개 13MB → WebP·640px, 도감 `loading="lazy"` — M
- [ ] 전날 체크(`final-demo-plan.md` 4절) — 관리자 → AI 사용량에서 일일 코멘트 AI 끄기, `PET_CHAT_DAILY_LIMIT` 낮추기, QR이 https인지, 전날 밤 DB 백업 1회 별도 — S

## 2. 정합성 복구 (문서 = 코드 = 운영)

- [ ] 신호등 경계값 결정 — 코드 `ExpenseCalculationService.java:38-39`(z==zRed → GRAY) vs `domain.md:57`(RED if z>=Z_RED). 한쪽을 고치고 `ExpenseCalculationServiceTest`에 경계 케이스 추가 — S
- [ ] `Income.java:51` columnDefinition에 `SALARY` 추가 — `V5__income_source_salary.sql:5` 기준 — S
- [ ] `domain.md` 내부 불일치 — `:44` N=10 → 5(`:128`), `:127` 클래스명 → `ExpenseCalculationService.java:23`, `:351` 관리자 1234 → env 방식 — S
- [ ] `db-spec.md`를 V55 기준으로 갱신 — 구글 컬럼 V26, users에 `login_count`·`last_record_reward_on`, `:156` 메모 규칙, `:163,624` stat_delta 폐지, `:298-299` receipt NULL·GEMINI, `:941-942` 없는 클래스/키 삭제 — M
- [ ] 주석 TBD 정리 — `Goal.java:63`, `Expense.java:82`, `AiInquiry.java:15`, `Pet.java:14,223`, `UserStatStats.java:46`(domain.md:403-410 확정값 반영); `GoogleTokenVerifier.java:23`·`V26:7` "기존 계정 연결" → 409 거절로 — S
- [x] (2026-11-02 반영) 배포 문서를 도커 기준으로 재작성 — 서버의 `docker-compose.yml`·`vori.worldpick.co.kr.conf`·`deploy.sh`를 `deploy/docker/`로 복사해 추적(비밀값 제외), `deploy/vori-backend.service`·`deploy/nginx/vori.conf`는 "참고용 초안" 표시 또는 삭제, `docs/deploy-checklist.md` 재작성(경로 `/data/home2/vori.worldpick.co.kr`, env 주입, `vori_private` 서브넷 172.16.240.0/24 고정 사유, 재배포·롤백·백업) — M
- [ ] `README.md` 갱신 — start.spring.io·CRA 생성 안내 삭제, 라우팅 표(5→20+ 또는 `docs/screens/IA.md` 링크), 도메인 패키지 표, `App.test.js` 행 삭제, `.env` 키 표에 `ADMIN_PASSWORD`, 구조 도식 `vori/` — S
- [x] (2026-11-02 반영) `docs/roadmap.md:~396` "클라우드 배포 + QR 미착수" → 완료, `docs/README.md` "EC2 + Nginx" 문구 — S
- [ ] (2026-11-02 `deploy/docker/app.env.example` 로 이전·키 추가 반영, `.env.example` 누락 키는 남음) `deploy/server.env.example` — `SESSION_COOKIE_SECURE` 키 추가(HTTP 단계 false), `server.address=127.0.0.1`에 "도커에서는 넣지 않음" 주석, `GEMINI_EMBEDDING_MODEL` 추가; `.env.example`에 `PET_CHAT_DAILY_LIMIT`·`AI_QUESTION_WORDING_ENABLED`·`AI_DAILY_COMMENT_ENABLED` — S
- [x] (2026-11-02 반영) `docs/deploy-checklist.md:65` 쿠키 secure 문구를 prod 기본 true에 맞춤, `deploy/nginx/vori.conf:6` CORS 서술(env화됨)·`:55-56` 타임아웃 근거 문구 — S
- [ ] `docs/tutorial-flow.md`의 `/today` 경로 정리(코드에 없음), `docs/screens/README.md`·`api-integration.md`는 현재 일치하므로 유지 — S

## 3. 배치·리포트 정확성

- [ ] 월간 리포트 cron을 익월 1일 00:30으로 — `MonthlyReportScheduler.java:22` `0 0 12 L * *` → `0 30 0 1 * *`, 대상 월을 전월로. `MonthlyReportService.java:59-62` 주석과 일치시킴 — S
- [ ] `settleAll` 트랜잭션 — `MonthlyReportService.java:45-56` 자기 호출로 `@Transactional` 미적용, 리포트 저장과 알림이 비원자적 → 별도 빈 또는 `TransactionTemplate` — S
- [ ] 일일 리포트 스탯 합계 ↔ 판정 보상 시각 — `DailyReportService.java:218` [D,D+1) 범위에 D+1 00:00 지급 보상 미포함(추정). 판정 행의 보상 값을 직접 사용 — M
- [ ] `DailyJudgmentResponse.java:56` 빈 catch에 WARN 로그(보상 파싱 실패가 조용히 0이 됨); `DailyReportService.java:185-187`·`ReceiptService.java:145` warn에 예외 객체 전달 — S
- [ ] 카테고리 임베딩 초기화 — `CategorizeService.java:115-118,148` `@PostConstruct` → `ApplicationReadyEvent`(시더 이후), 임베딩 결과 캐시(재기동마다 leaf 수만큼 호출 제거), 분류 요청 1인 제한(`:424`), 로그 경로 문구 `/api/categorize` 수정 — M
- [ ] `ReceiptOcrJob.linkExpense` 미연결 — `:102` 호출처 0, `receipts.expense_id` 항상 NULL. 연결할지(지출 저장 시 영수증 id 전달) 컬럼을 버릴지 결정 — M
- [ ] 스케줄러 실행 확인 — 2026-11-03 00:00·00:10·04:00·20:00 이후 `docker logs vori-backend --since 2026-11-03T00:00` 에서 `JudgmentRewardSettler`·`DailyReportScheduler` 흔적 — S

## 4. 보안 강화 (시연 후)

- [ ] CSRF — `SecurityConfig.java:85` 비활성. Origin/Referer 검사 필터 또는 쿠키 토큰. 쿼리만 받는 POST(`/eggs/buy`, `/furniture/buy`, `/pets/{id}/release`) 우선 — M
- [ ] GET 쓰기 제거 — `SpendingPlanController.java:14` `ensurePlan`을 POST 또는 지연 생성으로 — S
- [ ] Gemini 키를 `x-goog-api-key` 헤더로 — `GeminiClient.java:285,462` — S
- [ ] 로그 마스킹 — `GeminiClient.java:297` 지출 텍스트, `ReceiptService.java:102-103` OCR 원문 200자 → 길이만 — S
- [ ] 영수증 magic byte 검사 — `ReceiptService.java:161` — S
- [ ] 구글 토큰 JWKS 로컬 검증 + URL 인코딩 — `GoogleTokenVerifier.java:58,61` tokeninfo 사용 — M
- [ ] IAE 메시지 분리 — `GlobalExceptionHandler.java:101-108` 자체 예외 타입으로, 프레임워크 IAE는 일반 문구 — S
- [ ] `AttendanceService.java:78-84` 음수 아이템 ID 범위 검사(관리자 500) — S
- [ ] nginx `X-Frame-Options DENY`·CSP 추가(443 블록 + add_header 있는 location 안에도), dotfile `location ~ /\. { return 404; }`, `www.vori.worldpick.co.kr` server_name 추가 후 apex로 301 — `/home/dockers/nginx/conf.d/vori.worldpick.co.kr.conf` — S
- [ ] 세션 만료 처리 통일 — `api/admin.js`, `api/auth.js`를 `http.js` 래퍼 경유로 — S

## 5. 품질·유지보수

- [ ] 프론트 스모크 테스트 3개(로그인·지출 저장·영수증 업로드 화면) + `ci.yml` frontend 잡에 `npm test -- --watchAll=false`·`eslint` 단계 — M
- [ ] `react-router-dom` 7.18.2+ 업그레이드, `npm audit fix`(비파괴) — S
- [ ] CRA 잔재 제거 — `src/logo.svg`, `reportWebVitals.js`, `web-vitals`, `index.html:34` noscript 한글, `App.css:1` 주석 — S
- [ ] Node 22 고정 — 로컬 `nvm use`, `package.json` engines 유지, 배포는 `node:22-bookworm`(이미) — S
- [ ] Spring Boot 4 업그레이드 계획 — `build.gradle:3-5` 3.5 OSS 종료(2026-06-30), 선행으로 `AsyncConfig.java:25,29,44,45` `setConnectTimeout/ReadTimeout` → `requestFactorySettings` — L
- [ ] CRA → Vite 이전 검토 — `react-scripts` 5.0.1 유지보수 종료, audit critical 3·high 28의 근원 — L
- [ ] 고아 엔드포인트 9개·미사용 래퍼 14개 정리 — `/api/goals` 4개는 목표 기능 화면을 만들지 삭제할지 결정, `POST/PUT /api/expenses`·`POST /incomes`·`POST /savings`는 `/ledger/entries`로 통일, `budget.js`·`report.js`·월간·영수증 조회 래퍼 — M
- [ ] `WalletConfirmPage` 고아 화면 — `App.js:325` 삭제 또는 진입 경로 — S
- [ ] 어드민 "준비 중" 9개 메뉴 — `adminNav.js` `soon:true` 유지(명시 표기 OK) 또는 메뉴에서 제거 — S
- [ ] 롤백 절차 — `deploy.sh`에 배포 전 `backend-<커밋>.jar` 보존과 `--rollback <커밋>` 옵션, 프론트 이전 build 1개 보존 — M
- [ ] Suspense fallback 스피너(`App.js:136,290`), `alert`/`confirm` 9곳 모달화(이제 공통 `.modal`·`.modal-actions` 로 — `docs/design-system.md` 4절), `viewport-fit=cover` — S
- [ ] 문구 통일 — "졸업작품"(`LoginPage.jsx:225`, `SignupPage.jsx:317`), 약관·방침 "초안"(`LegalLayout.jsx:35,41`) — S

### 디자인 통일 (2026-11-03, 브랜치 `redesign/ui-unify`)

- [x] 디자인 토큰·공통 컴포넌트 도입 — `src/styles/tokens.css`·`components.css`, 규칙 `docs/design-system.md`, 근거 `docs/design-audit-2026-11-03.md`, 화면 비교 `docs/design-audit/before|after` — L
  - 라운드 36종 → 5단계, 그림자 120종 → 토큰 7종 중심, 그라디언트 51종 → 2종(+예외), 색 리터럴 492종 → 218종, 페이지별 색 변수 11세트 → 1세트
  - 버튼 15계열 → `.btn`, 모달·시트 8종 → `.modal`·`.sheet`(오버레이·라운드·그림자·버튼 줄·애니메이션 한 가지)
- [ ] 남은 세부 — 랜딩 헤더 버튼(`.landing-btn`)을 `.btn` 으로 합칠지 결정 / 화면 안 확인 영역(탈퇴 확인·가구 회수)을 `.modal` 로 옮기기(위 `alert`/`confirm` 모달화와 같이) / 카드 클래스(`.hm-card`·`.sp-card`·`.mr-card`·`.wr-card`·`.home-card`)를 JSX 에서 `.card` 로 합치기(지금은 CSS 값만 같다) / PC 대시보드 폭(1080~1420px) 통일 / `confirm()` 으로 남은 펫 배웅 확인 — M

## 6. 계획 미결 (사용자 결정 필요)

- [ ] PWA·서비스워커·웹푸시(`roadmap.md` "아직 안 정함") — 넣을지, 넣으면 저녁 회고 알림과 묶을지
- [ ] 게스트 체험 모드(`roadmap.md:~396` 미착수) — 관람객 가입 부담 vs 데이터 품질
- [ ] 절약액 원화 표시 + 공유 카드(`navigator.share`)(`plan-vs-reality.md`)
- [ ] 스탯 상한·충동소비 감점(`plan-vs-reality.md:67,69` 🔴/🟡)
- [ ] 사유 자기신고(CEREMONY 칩)로 RED→GREEN 되는 횟수 상한(`AiInquiryService.java:166-182`)
- [ ] 어드민 메뉴 범위 — 9개 "준비 중" 중 대회까지 만들 것
- [ ] `receipts.expense_id` 연결 여부(3절)와 `/api/goals` 기능 존치(5절)

## 정합성 유지 원칙

1. 문서 SSOT는 `docs/domain.md`(계산식·상태머신)와 `docs/db-spec.md`(스키마)다. 계산식·임계치·컬럼을 바꾸는 PR은 같은 PR에서 두 문서를 고친다.
2. 머지된 Flyway `V*.sql`은 수정하지 않는다(CI `migrations-unchanged`가 막는다). 스키마 변경은 새 버전으로만.
3. 설정 키를 추가하면 `.env.example`, `frontend/.env.example`, `deploy/server.env.example` 세 곳을 함께 갱신하고, 프론트 빌드에 박히는 값(`REACT_APP_*`)은 서버 `.env`와 짝을 명시한다.
4. 운영 변경은 `deploy.sh`만 거친다. 서버에서 직접 파일을 고치지 않고, 서버 전용 파일(compose·nginx conf·deploy.sh)은 `deploy/docker/`에 추적해 레포가 운영을 재현할 수 있게 한다.
5. 시간 상수(판정 시각·알림 시각·배치 cron)는 한 설정값에서 읽는다. 상수와 설정값이 갈라지면 시연 설정을 바꿀 때 어긋난다.
6. 엔드포인트를 추가하면 `docs/api-integration.md`와 `frontend/src/api/`를 같이 갱신하고, 쓰지 않는 쪽은 지운다.
7. 배포 후 점검은 `docs/AUDIT-YYYY-MM-DD.md`로 남기고 이전 보고서와 지표(P0/P1/P2, 테스트 수, 고아 수)를 비교한다.
