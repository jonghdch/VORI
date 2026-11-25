# 운영 배포 점검표 (도커, vori.worldpick.co.kr)

> 갱신 2026-11-03 · 상태: **운영 중** (`https://vori.worldpick.co.kr`, 커밋 `3481a4a`)
>
> 서버 설정 파일의 사본은 [`deploy/`](../deploy/README.md)에 있다(서버가 원본, 레포와 같게 유지).
> 경진대회(11/11)에서 관람객이 휴대폰으로 QR 을 찍고 직접 가입해 쓰는 것을 기준으로 한다.

## 한눈에

```
휴대폰 ─ https://vori.worldpick.co.kr ─ nginx 컨테이너 ┬─ /      → VORI/frontend/build (정적 파일, nginx 가 직접 서빙)
                                      (공용, 80/443)   └─ /api/  → vori-backend:8080 ─ vori-mysql:3306
```

| 항목 | 값 |
|---|---|
| 서버 | `ssh zzemsoft` (관리자 접속은 root 지만 **vori 작업은 vori 로** — 아래 원칙), 호스트명 iteasy, 공인 IP 1.240.13.103. 메모리 7.7GiB — 빠듯하다 |
| 도메인 | `vori.worldpick.co.kr` (www 없음). HTTPS 필수 — 구글 로그인은 IP 로 안 된다 |
| 프로젝트 디렉터리 | `/data/home2/vori.worldpick.co.kr` (= `vori` 홈, 아래 전부 `vori:vori` 소유) |
| 실행 사용자 | `vori`(uid/gid 1027, 셸 /bin/bash, 비밀번호 잠금, `docker` 그룹). 컨테이너 vori-backend·vori-mysql 도 `user: 1027:1027` |
| 메모리 실사용 | 백엔드 약 430MiB, MySQL 약 190MiB |

### 서버 경로

| 경로 (`/data/home2/vori.worldpick.co.kr/` 아래) | 내용 |
|---|---|
| `VORI/` | 레포 git clone (main). `VORI/.env` = 앱 설정(vori 소유 600, 비밀값) |
| `docker-compose.yml` | compose 프로젝트 `vori` ([사본](../deploy/docker/docker-compose.yml)) |
| `.env` | compose 보간용 `MYSQL_ROOT_PASSWORD`·`DB_PASSWORD` (vori 600, [키 목록](../deploy/docker/compose.env.example)) |
| `deploy.sh` | 일상 배포 스크립트, vori 로 실행 (vori 750, [사본](../deploy/docker/deploy.sh)) |
| `deploy-ssh-entry.sh` | GitHub Actions 배포 키의 진입점 — 인자 화이트리스트 후 deploy.sh 실행 (vori 750, [사본](../deploy/docker/deploy-ssh-entry.sh)) |
| `.ssh/authorized_keys` | vori 의 GitHub Actions 배포 키 한 줄(강제 명령, vori 600, 디렉터리 700) |
| `.deploy-state/` | deploy.sh 의 잠금·`.env` 해시 상태, GitHub Actions 배포 키 `github-deploy-key`(vori 600)·`.pub`, 소유 전환 전 DB 백업 `pre-chown-backup-20261103-001916.sql.gz`(600) |
| `logs/deploy.log` | 배포 로그 |
| `mysql-data/` | MySQL 데이터(백업 대상, vori 700 — mysqld 가 uid 1027 로 돈다) |
| `.gradle/`, `.npm/` | 빌드 캐시 |

nginx 설정 `/home/dockers/nginx/conf.d/vori.worldpick.co.kr.conf` ([사본](../deploy/nginx/vori.worldpick.co.kr.conf)), 인증서 `/home/dockers/certbot/{conf,data}`.

### 권한 원칙 (2026-11-03 전환)

- **모든 파일은 `vori:vori` 소유, deploy.sh 는 vori 로 실행, root 로 작업하지 않는다.** 서버에 root 로 들어왔으면 `runuser -u vori -- <명령>` 으로 한다(`cd /data/home2/vori.worldpick.co.kr` 후 — vori 는 `/root` 를 못 읽어 docker compose 가 `stat .: permission denied` 를 낸다). root 로 파일을 만들면 vori 로 도는 deploy.sh 가 그 파일을 못 고쳐 다음 배포가 실패한다.
- deploy.sh 는 root 로 실행하면 "vori 로 실행하세요" 안내 후 exit 1 한다. 빌드 컨테이너(temurin·node)도 `--user 1027:1027` 이라 산출물이 vori 소유로 나온다.
- vori 의 docker 권한은 `docker` 그룹(`usermod -aG docker vori`)으로 준다. rootless docker 는 공용 nginx·`dockers_backend` 네트워크를 같이 쓸 수 없어 쓰지 않는다.
- **주의: `docker` 그룹은 사실상 root 와 같은 권한이다**(호스트 `/` 를 bind 한 컨테이너를 띄우면 호스트 root 가 된다). 그래서 vori 계정과 그 배포 키(아래 2-3절)는 root 계정처럼 다뤄야 한다 — 배포 키는 forced command 로 deploy-ssh-entry.sh 만 돌게 묶여 있다.
- vori-mysql 은 `user: "1027:1027"` 로 돈다. 공식 이미지의 `/var/lib/mysql-files`(mysql 999, 750) 에 접근할 수 없어 `--secure-file-priv=NULL` 로 파일 import/export 를 끈다(앱은 쓰지 않는다). 이미지로 되돌려 uid 999 로 띄우려면 compose 의 `user` 줄과 이 옵션을 빼고 `chown -R 999:999 mysql-data`.

### 컨테이너·네트워크

| 이름 | 이미지 | 네트워크 | 비고 |
|---|---|---|---|
| `vori-mysql` | mysql:8.4 | `vori_private` (internal) | 호스트 포트 없음. mem_limit 512m. `user: 1027:1027`, `--secure-file-priv=NULL` |
| `vori-backend` | eclipse-temurin:17-jre | `vori_private` + `dockers_backend` | `user: 1027:1027`. 호스트 포트 publish 없음. prod 프로필, Xmx512m, mem_limit 900m. 레포 `VORI` 를 `/app` 으로 bind, 작업 디렉터리 `/app` 의 `.env` 를 읽음, jar 를 `/tmp/app.jar` 로 복사해 실행 |
| `nginx` | dockers-nginx (기존 공용) | `dockers_backend` | 80/443. `/data/home2` 가 같은 경로로 bind 되어 있어 `frontend/build` 를 root 로 직접 서빙 |

- `vori_private` 는 **서브넷 172.16.240.0/24 로 고정**한다. 호스트 방화벽 `DOCKER-USER` 의 `zzem-db-guard` 가 출발지가 172.16.0.0/12 밖인 3306 트래픽을 DROP 하는데, 자동 할당되는 192.168.x 대역이면 DB 연결이 막힌다.
- 백엔드는 jar 를 `/tmp/app.jar` 로 복사해서 띄운다 — 재빌드가 실행 중인 jar 를 덮어써도 JVM 이 깨지지 않게.
- 호스트 포트를 열지 않으므로 8080·3306 은 밖에서 닿지 않는다.

## 1. 최초 구축 절차 (2026-11-02 완료 — 재현용)

1. **사용자·디렉터리**: `vori` 사용자(uid/gid 1027) 생성, `/data/home2/vori.worldpick.co.kr` 를 vori 소유로 만든다. `useradd -m -d /data/home2/vori.worldpick.co.kr vori` 류.
2. **clone**: `runuser -u vori -- git clone <레포 URL> /data/home2/vori.worldpick.co.kr/VORI` (main).
3. **compose·env 작성**: 같은 디렉터리에 `docker-compose.yml`(`deploy/docker/` 에서 복사)과 `.env`(`compose.env.example` 의 키를 무작위 값으로 채움, `chmod 600`). `VORI/.env` 는 `app.env.example` 를 보고 작성(`DB_PASSWORD` 는 compose `.env` 와 같게, `ADMIN_PASSWORD` 는 길고 무작위로, `chmod 600`, 소유 vori). `deploy.sh` 도 복사해 `chmod 750`.
4. **공유망**: `dockers_backend` 는 기존 nginx 스택이 만든 external 네트워크다(없으면 compose 가 실패).
5. **빌드·기동**: `deploy.sh --force-build all` 이 jar 와 프론트를 빌드하고 `docker compose -p vori up -d` 까지 한다. 첫 기동 때 Flyway 가 V1 부터 전부 적용한다(`ddl-auto=validate` — 테이블은 직접 만들지 않는다). 막 만든 컨테이너를 곧바로 restart 하면 첫 Flyway 가 끊길 수 있어 deploy.sh 는 새로 만들 때 restart 하지 않는다.
6. **인증서**: DNS(A 레코드)를 서버 IP 로 맞춘 뒤 webroot 방식으로 발급한다. 명령은 [nginx conf 상단 주석](../deploy/nginx/vori.worldpick.co.kr.conf)의 `docker run ... certbot/certbot certonly --webroot ...`. 80 포트의 ACME 응답은 기존 `00-default.conf` 가 처리한다. 발급 전에는 443 server 블록이 인증서 파일이 없어 nginx -t 가 실패하므로, 80 블록만 먼저 두고 발급한 뒤 443 블록을 넣는다.
7. **nginx conf**: `deploy/nginx/vori.worldpick.co.kr.conf` 를 `/home/dockers/nginx/conf.d/` 에 복사 → `docker exec nginx nginx -t && docker exec nginx nginx -s reload`.
8. **확인**: 아래 3절.

이 conf 에 꼭 있어야 하는 것:
- `try_files $uri $uri/ /index.html` — 없으면 `/home` 같은 화면 주소를 새로고침할 때 404 (화면은 React 라우터가 그린다)
- `client_max_body_size 11m` — 서버 상한 10MB(요청 11MB). 기본 1MB 면 영수증 사진이 413
- `proxy_read_timeout 420s`·`proxy_send_timeout 420s` — Gemini 를 기다리는 요청(펫 대화·사유 분류·영수증 인식)은 최악 약 6분(`GeminiClient`). 기본 60초면 504
- `resolver 127.0.0.11` + 변수 `proxy_pass` — 컨테이너를 다시 만들어 IP 가 바뀌어도 reload 없이 Docker DNS 를 다시 조회
- HTTPS 헤더(HSTS 등) — `.co.kr` 은 HTTPS 가 자동 강제되지 않아 직접 건다

## 2. 일상 배포

```bash
ssh zzemsoft 'cd /data/home2/vori.worldpick.co.kr && runuser -u vori -- ./deploy.sh'
```

평소에는 main 에 머지하면 GitHub Actions 가 자동으로 이 스크립트를 돌린다(2-3절). 손으로 돌리는 것은 `.env` 변경·롤백·Actions 장애 때.

`git pull --ff-only` → 바뀐 파일을 보고 필요한 것만 한다.

| 바뀐 곳 | 동작 | 소요 |
|---|---|---|
| `backend/` | 임시 JDK 컨테이너로 jar 빌드(캐시 `.gradle`) + `docker compose restart backend`, `/api/auth/me` 가 401 이 될 때까지 대기 | 약 25~60초 |
| `frontend/` | 임시 node:22 컨테이너로 `.frontend-stage` 에 빌드 후 `frontend/build` 를 원자 교체(nginx reload 불필요) | 약 2분 |
| `VORI/.env` | 지난 기동 이후 바뀌었으면 백엔드 재기동. `GOOGLE_CLIENT_ID`·`vori.ai-judge.open-hour` 가 바뀌었으면 프론트도 재빌드 | 위와 같음 |
| 그 외(docs, scripts, deploy …) | 레포가 컨테이너에 bind 되어 있어 pull 만으로 반영, 빌드 없음 | 즉시 |

옵션: `--force-build [all|backend|frontend]`(변경이 없어도 빌드, 값 생략 시 all), `--no-pull`(pull 생략), `-h`.
- **vori 로 실행한다**(root 면 거부). `flock` 으로 동시 실행을 막는다.
- 로그: `logs/deploy.log`(콘솔에도 같이 출력). 마지막에 `백엔드 정상 (401)` 이 나오면 성공.
- 프론트 빌드 값은 deploy.sh 가 `VORI/.env` 의 `GOOGLE_CLIENT_ID` 와 `vori.ai-judge.open-hour` 를 읽어 `REACT_APP_GOOGLE_CLIENT_ID`·`REACT_APP_AI_ACTIVE_FROM_HOUR` 로 넘긴다(빌드 때 박힌다). `REACT_APP_API_BASE` 는 넣지 않는다 → `/api` 로 부른다(#122).
- 서버에서 파일을 직접 고치지 말고 레포에 머지한 뒤 deploy.sh 로 반영한다. 서버 전용 파일(compose·nginx conf·deploy.sh)은 [`deploy/README.md`](../deploy/README.md) 규칙을 따른다.

### 2-1. `.env` 값 바꾸기 (GEMINI_API_KEY·GOOGLE_CLIENT_ID 입력)

1. 서버에서 `VORI/.env` 를 편집한다(`vori` 소유 600 유지, 값에 따옴표 금지). 키 목록·설명은 [`app.env.example`](../deploy/docker/app.env.example).
   - `GEMINI_API_KEY` — 유료(결제 등록) 프로젝트 키. 비어 있으면 AI 기능 전부가 동작하지 않는다(로그 `CategorizeService 카테고리 임베딩 초기화 실패`).
   - `GOOGLE_CLIENT_ID` — 비우면 구글 버튼이 숨는다.
2. `deploy.sh` 를 한 번 실행한다. `.env` 변경을 감지해 백엔드를 재기동하고, `GOOGLE_CLIENT_ID` 가 바뀌었으면 프론트도 자동 재빌드한다.
3. 필수 설정 요점: `spring.datasource.url=jdbc:mysql://vori-mysql:3306/vori?...` 는 컨테이너에서 필수(기본값 127.0.0.1 은 컨테이너 자기 자신). `server.servlet.session.cookie.secure=true`·`server.forward-headers-strategy=native` 는 HTTPS 뒤라 켜 둔다(prod 프로필의 쿠키 secure 기본값도 true — http 로만 시험할 때만 `SESSION_COOKIE_SECURE=false`). `server.address=127.0.0.1` 은 넣지 않는다(nginx 가 못 붙는다).

### 2-2. 시연 설정 (판정 시각 open-hour=0)

낮 발표·경진대회에는 판정 시각을 0 으로 연다. **`VORI/.env` 의 `vori.ai-judge.open-hour=0` 한 곳만 바꾸고 `deploy.sh`** 를 실행하면 프론트(`REACT_APP_AI_ACTIVE_FROM_HOUR`)도 자동으로 재빌드된다(약 2분). 끝나면 20 으로 되돌리고 다시 `deploy.sh`.

- 주의: `JudgmentOpenNotifier` 의 알림은 **20시 고정**이라 open-hour 를 따라가지 않는다. 낮에 열어도 알림은 20시에 간다.
- 이 값을 빌드 없이 바꾸면 백엔드와 프론트가 어긋나 일반 계정이 판정을 못 한다.

### 2-3. GitHub Actions 자동 배포 (2026-11-03 구성)

```
main push ─▶ CI(.github/workflows/ci.yml, 이름 "CI") ─ 성공 ─▶ Deploy(.github/workflows/deploy.yml)
        ─ ssh vori@서버 ─▶ (forced command) deploy-ssh-entry.sh ─▶ deploy.sh (모두 vori 권한) ─▶ 배포 후 확인(/api/auth/me 401, / 200)
```

- **트리거**: `workflow_run` — 이 저장소 `main` 으로의 push 에서 돈 CI 가 `success` 로 끝났을 때만. PR·포크에서 돈 CI 는 배포하지 않는다. CI 가 실패하면 배포도 없다.
- **겹침 방지**: `concurrency: deploy-production`(진행 중 배포는 취소하지 않고 다음 것이 기다림) + 서버 deploy.sh 의 `flock`. 수동으로 deploy.sh 를 돌리는 중이면 Actions 쪽은 `다른 deploy.sh 가 실행 중입니다` 로 실패한다 → 끝난 뒤 Re-run.
- **서버는 항상 `origin/main` 최신을 pull** 한다. CI 가 끝나기 전에 main 에 커밋이 더 들어왔으면 그 커밋까지 함께 배포된다(그 커밋의 CI 결과로 한 번 더 돌지만 "새 커밋 없음" 으로 끝난다).
- **수동 실행**: GitHub → Actions 탭 → `Deploy` → `Run workflow`(브랜치 main) → `force_build` 선택(`none` = 변경 감지에 맡김 / `backend` / `frontend` / `all`). main 이 아닌 브랜치에서 실행하면 잡이 건너뛰어진다.
- **실패 시 보는 곳**: ① Actions 실행 로그(서버 deploy.sh 출력이 그대로 찍힌다) ② 서버 `logs/deploy.log` ③ 백엔드 이상이면 `docker logs vori-backend`. 배포 후 확인 단계만 실패했다면 배포 자체는 끝났고 응답이 이상한 것이다.

**서버 측 구성** (vori 계정 + 강제 명령 — root 의 authorized_keys 에는 아무것도 등록하지 않는다, sudo 없음)

| 항목 | 값 |
|---|---|
| 사용자 | `vori` (셸 `/bin/bash` — forced command 실행에 필요, 비밀번호 잠금, 홈 = 프로젝트 디렉터리) |
| 키 | `/data/home2/vori.worldpick.co.kr/.ssh/authorized_keys`(vori 600, `.ssh` 700) 한 줄: `command="/data/home2/vori.worldpick.co.kr/deploy-ssh-entry.sh",no-pty,no-port-forwarding,no-X11-forwarding,no-agent-forwarding ssh-ed25519 … github-deploy-vori` |
| 래퍼 | `deploy-ssh-entry.sh` — 클라이언트가 보낸 문자열(`SSH_ORIGINAL_COMMAND`)을 셸로 해석하지 않고 공백으로만 쪼개, 허용 인자 `--force-build all\|backend\|frontend`, `--no-pull` 만 deploy.sh 로 넘긴다. 그 밖의 인자가 하나라도 있으면 `logs/deploy.log` 에 `deploy-ssh-entry 거부` 를 남기고 exit 1 |
| sshd | 포트 38371, `PasswordAuthentication no`, `StrictModes yes`, `AllowUsers` 없음(vori 허용). ufw 가 38371/tcp 를 모든 출발지에 열어 둠, fail2ban `sshd` jail 있음(키 인증 성공은 실패로 세지 않음) |

- 시험(2026-11-03): `ssh … vori@서버 -- --no-pull` → deploy.sh 정상 종료(새 커밋 없음), `ssh … vori@서버 id` → 거부 exit 1, `--no-pull;id`·`$(id)` → 거부, 포트 포워딩·pty·sftp 거부, 같은 키로 root 접속 → Permission denied.
- 로컬에서 ssh 로 옵션을 넘길 때는 `ssh … vori@1.240.13.103 -- --no-pull` 처럼 `--` 를 넣는다(없으면 로컬 ssh 가 `--no-pull` 을 자기 옵션으로 읽는다).

**GitHub secrets** (Settings → Secrets and variables → Actions → Repository secrets, 워크플로는 `environment: production` 을 쓰지만 secret 은 저장소 수준에 둔다)

| 이름 | 값 | 가져오는 곳 |
|---|---|---|
| `DEPLOY_SSH_KEY` | 배포 개인키 전체(`-----BEGIN OPENSSH PRIVATE KEY-----` ~ `END` 줄까지) | `ssh zzemsoft cat /data/home2/vori.worldpick.co.kr/.deploy-state/github-deploy-key` |
| `DEPLOY_HOST` | `1.240.13.103` | — |
| `DEPLOY_PORT` | `38371` | `~/.ssh/config` 의 zzemsoft 항목 |
| `DEPLOY_KNOWN_HOSTS` | 호스트 키 한 줄(`[1.240.13.103]:38371 ssh-ed25519 AAAA…`) | `ssh-keyscan -p 38371 -t ed25519 1.240.13.103 2>/dev/null` — 지문 `SHA256:vUuQrNd1Ls/THJP9HmN3vMcu5Nxf6ryNKhgSdIbPChQ` 와 같은지 확인 |

**키 재발급** (유출 의심, 담당자 교체 시) — vori 로 실행한다

```bash
ssh zzemsoft
cd /data/home2/vori.worldpick.co.kr
runuser -u vori -- bash -c 'set -e; S=/data/home2/vori.worldpick.co.kr; umask 077
  rm -f $S/.deploy-state/github-deploy-key $S/.deploy-state/github-deploy-key.pub
  ssh-keygen -q -t ed25519 -N "" -C github-deploy-vori -f $S/.deploy-state/github-deploy-key
  printf "command=\"%s\",no-pty,no-port-forwarding,no-X11-forwarding,no-agent-forwarding %s\n" \
    $S/deploy-ssh-entry.sh "$(cat $S/.deploy-state/github-deploy-key.pub)" > $S/.ssh/authorized_keys
  chmod 600 $S/.ssh/authorized_keys'
```

`authorized_keys` 를 통째로 새 키 한 줄로 바꾸므로 옛 키는 즉시 무효가 된다. 그다음 GitHub secret `DEPLOY_SSH_KEY` 를 새 개인키로 갱신하고 Actions 에서 `Deploy` 를 수동 실행해 확인한다. 자동 배포를 잠시 멈추려면 `authorized_keys` 를 비우거나 Actions 에서 `Deploy` 워크플로를 Disable 한다.

## 3. 확인

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://vori.worldpick.co.kr/            # 200 (프론트)
curl -s -o /dev/null -w '%{http_code}\n' https://vori.worldpick.co.kr/home        # 200 (try_files 폴백)
curl -s -o /dev/null -w '%{http_code}\n' https://vori.worldpick.co.kr/api/auth/me # 401 이면 백엔드 정상(로그인 전)
curl -sI http://vori.worldpick.co.kr/ | head -3                                    # 301 → https
```

휴대폰으로:
- [ ] `https://vori.worldpick.co.kr` 첫 화면, 아무 화면에서 새로고침(404 가 아닌지)
- [ ] 가입 → 설문 → 홈 (펫 이름·출석 팝업 순서)
- [ ] 지출 기록 → 카테고리 자동 분류 (GEMINI_API_KEY 필요)
- [ ] 영수증 사진(큰 사진) 올리기 — 413·504 가 나지 않는지
- [ ] 판정하기 → 사유 답변 → 최종 판정 (판정 시각 설정 확인)
- [ ] 구글 로그인 (GOOGLE_CLIENT_ID + 콘솔 설정 필요, 5절)
- [ ] 관리자 로그인 → AI 사용량 화면(Gemini 한도·스위치)
- [ ] 시연 계정: `VORI_BASE_URL`·`VORI_ADMIN_PASSWORD`·`VORI_DEMO_PASSWORD` 를 주고 `scripts/demo_seed.py`(#126 — 기본 비밀번호면 멈춘다)

## 4. 운영

```bash
ssh zzemsoft "docker logs -f vori-backend"                 # 백엔드 로그
ssh zzemsoft "tail -f /data/home2/vori.worldpick.co.kr/logs/deploy.log"
ssh zzemsoft "docker compose -p vori -f /data/home2/vori.worldpick.co.kr/docker-compose.yml ps"
ssh zzemsoft "docker stats --no-stream vori-backend vori-mysql"   # 메모리 (서버가 빠듯하다)
```

- 컨테이너는 `restart: unless-stopped`, 로그는 크기 제한(backend 20m×5, mysql 10m×3).
- 모니터링(가동·디스크·메모리 알림)은 아직 없다 — [`docs/remaining-work.md`](remaining-work.md) 0절.
- 이미 머지된 **Flyway 마이그레이션 파일은 고치지 않는다**(체크섬이 깨져 서버가 안 뜬다). 스키마 변경은 새 V 파일로.

### 인증서 갱신

Let's Encrypt webroot 방식. root crontab 에 매일 03:30 `/home/dockers/certbot_renew.sh`(전체 `certbot renew` + nginx reload)가 돈다. 현재 만료 2027-01-31. 갱신 로그 `/home/dockers/certbot/logs/renew-cron.log`. 만료 30일 전 이후 갱신이 안 되면 로그를 보고 80 포트 ACME 응답(`/.well-known/acme-challenge`)을 확인한다.

## 5. 구글 로그인 콘솔 설정

- [ ] Google Cloud 콘솔 > 사용자 인증 정보 > 그 OAuth 클라이언트 > **「승인된 JavaScript 원본」에 `https://vori.worldpick.co.kr`**
  - 구글 버튼(ID 토큰 방식)은 리디렉션 URI 가 아니라 JavaScript 원본을 본다
- [ ] OAuth 동의 화면의 게시 상태 확인 — 「테스트」면 등록한 테스트 사용자만 로그인된다. 관람객이 쓰려면 「프로덕션」으로(콘솔에서 확인 필요)
- [ ] `VORI/.env` 에 `GOOGLE_CLIENT_ID` 입력 후 `deploy.sh`(2-1절)

## 6. 롤백 (현재 수동 — 자동 롤백은 남은 작업)

이전 jar·build 를 따로 보존하지 않으므로 이전 커밋을 체크아웃해 다시 빌드한다.

```bash
ssh zzemsoft
cd /data/home2/vori.worldpick.co.kr
runuser -u vori -- git -C VORI checkout <되돌릴 커밋>
runuser -u vori -- ./deploy.sh --no-pull --force-build all      # 백엔드+프론트 재빌드(약 3분)

# 문제가 해결돼 최신으로 돌아올 때
runuser -u vori -- git -C VORI checkout main
runuser -u vori -- ./deploy.sh --force-build all                # main 최신으로 pull 후 재빌드
```

- 롤백 중에는 GitHub Actions 자동 배포가 끼어들지 않게 Actions 에서 `Deploy` 워크플로를 Disable 해 둔다(끝나면 Enable).
- 롤백 중에는 detached HEAD 라 `git pull --ff-only` 가 실패하므로 `--no-pull` 을 쓴다. 끝나면 반드시 `git checkout main`.
- **DB 는 되돌려지지 않는다.** 되돌릴 커밋보다 새로운 Flyway 마이그레이션이 이미 적용돼 있으면 옛 코드가 `ddl-auto=validate` 에서 안 뜰 수 있다. 스키마가 바뀐 배포는 백업(7절)이 있어야 안전하게 되돌릴 수 있다.
- 배포 전 `backend-<커밋>.jar` 보존과 `--rollback <커밋>` 옵션은 남은 작업([`remaining-work.md`](remaining-work.md)).

## 7. 백업 (아직 없음 — P0)

현재 정기 DB 백업이 없다(`remaining-work.md` 0절, 2026-11-03 소유 전환 직전 1회분만 `.deploy-state/pre-chown-backup-20261103-001916.sql.gz`). 권장 설계: vori crontab 에 매일 새벽 `docker exec vori-mysql mysqldump --single-transaction --routines -uroot -p"$MYSQL_ROOT_PASSWORD" vori | gzip > /data/backup/vori/vori-$(date +%F).sql.gz`(비밀번호는 compose 루트 `.env` 에서 읽음, 파일 권한 600) 를 돌리고, 7~14일치만 남기고 지우며, 가능하면 다른 호스트/오브젝트 스토리지로 하루 한 번 복사한다. 경진대회 전날은 수동으로 한 번 더 받고, 복원 리허설(빈 컨테이너에 `gunzip | mysql`)을 한 번 해 본다.

## 금지 사항

- **다른 컨테이너를 건드리지 않는다.** 이 서버는 여러 서비스가 공유한다. `docker compose -p vori ...` 와 `vori-*` 이름만 다룬다. `docker rm`/`prune`/`down` 을 프로젝트 지정 없이 쓰지 않는다.
- **`nginx` 컨테이너를 restart 하지 않는다.** 설정 반영은 `docker exec nginx nginx -t && docker exec nginx nginx -s reload` 만. restart 하면 같은 nginx 를 쓰는 모든 사이트가 끊긴다.
- **이미 머지된 Flyway 마이그레이션을 수정하지 않는다**(새 V 파일로).
- `VORI/.env`·compose 루트 `.env` 의 값을 저장소·대화·로그에 남기지 않는다.
- 서버의 compose·deploy.sh·deploy-ssh-entry.sh·nginx conf 를 고치면 같은 변경을 `deploy/` 에도 반영한다.
- **vori 디렉터리 아래에서 root 로 파일을 만들거나 deploy.sh 를 root 로 돌리지 않는다**(소유가 섞이면 자동 배포가 깨진다). 섞였으면 `chown -R vori:vori /data/home2/vori.worldpick.co.kr`.
- root 의 `~/.ssh/authorized_keys` 에 배포 키를 넣지 않는다.

## 경진대회 날

- [ ] 전날 밤: 관리자 → AI 사용량에서 **일일 코멘트 AI 끄기**(0시 10분 배치가 한도를 쓴다, final-demo-plan 4절)
- [ ] 전날: DB 수동 백업 1회(7절)
- [ ] 판정 시각 0(`VORI/.env` 한 곳 + `deploy.sh`, 2-2절)
- [ ] `PET_CHAT_DAILY_LIMIT` 낮추기(게스트가 많으면) → `deploy.sh`
- [ ] QR 이 가리키는 주소가 `https://vori.worldpick.co.kr` 인지
