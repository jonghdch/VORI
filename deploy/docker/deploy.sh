#!/usr/bin/env bash
# VORI 배포 — vori 사용자로 실행한다:
#   ssh zzemsoft runuser -u vori -- /data/home2/vori.worldpick.co.kr/deploy.sh [옵션]
#   (GitHub Actions 는 vori 계정 배포 키 → deploy-ssh-entry.sh → 이 스크립트)
#
#   git pull → 바뀐 파일을 보고 필요한 것만 빌드/재기동한다.
#     backend/  바뀜 → jar 재빌드(임시 JDK 컨테이너) + vori-backend restart
#     frontend/ 바뀜 → 프론트 재빌드(임시 node 컨테이너) → frontend/build 교체 (nginx reload 불필요)
#     그 외(docs, scripts, deploy …) → 레포가 bind volume 이라 pull 만으로 컨테이너에 반영됨
#   레포 .env 가 지난 기동 이후 바뀌었으면 backend restart, GOOGLE_CLIENT_ID·open-hour 가 바뀌었으면 프론트 재빌드.
#
# 옵션
#   --force-build [all|backend|frontend]   변경이 없어도 빌드(+backend restart). 값 생략 시 all
#   --no-pull                              git pull 을 건너뛴다(로컬 상태 그대로 빌드 판단)
#   -h | --help
#
# vori 로 실행한다(docker 그룹 권한으로 docker 사용, root 금지). 파일·빌드 산출물·컨테이너 모두 vori(1027) 소유/권한.
set -euo pipefail

H=/data/home2/vori.worldpick.co.kr
REPO="$H/VORI"
COMPOSE=(docker compose -p vori -f "$H/docker-compose.yml")
VORI_UID=1027; VORI_GID=1027
LOG="$H/logs/deploy.log"
STATE="$H/.deploy-state"
JAR="$REPO/backend/build/libs/backend-0.0.1-SNAPSHOT.jar"

force=""; pull=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force-build)
      if [[ "${2:-}" =~ ^(all|backend|frontend)$ ]]; then force="$2"; shift; else force=all; fi ;;
    --no-pull) pull=0 ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
  shift
done

if [[ $EUID -eq 0 ]]; then
  echo "root 로 실행하지 마세요 — vori 로 실행하세요: runuser -u vori -- $0 $*" >&2; exit 1
fi
[[ $(id -u) -eq $VORI_UID ]] || { echo "vori(uid $VORI_UID) 로 실행하세요 (현재 $(id -un))" >&2; exit 1; }
id -nG | grep -qw docker || { echo "vori 가 docker 그룹에 없습니다 (usermod -aG docker vori)" >&2; exit 1; }
export HOME="$H"   # runuser 로 들어오면 HOME 이 /root 로 남아 git·docker 가 설정 파일을 못 읽는다
cd "$H"   # runuser 로 root 홈에서 들어오면 docker compose 가 cwd 를 못 읽는다
mkdir -p "$H/logs" "$STATE"
exec 9>"$STATE/deploy.lock"
flock -n 9 || { echo "다른 deploy.sh 가 실행 중입니다" >&2; exit 1; }
exec > >(tee -a "$LOG") 2>&1
log() { echo "[$(date '+%F %T')] $*"; }

env_get() { # 레포 .env 에서 키 값을 읽는다(없으면 기본값)
  local v; v=$(grep -E "^[[:space:]]*$1[[:space:]]*=" "$REPO/.env" 2>/dev/null | tail -1 | cut -d= -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//') || true
  echo "${v:-$2}"
}

log "===== deploy 시작 (force=${force:-none}, pull=$pull)"
old=$(git -C "$REPO" rev-parse HEAD)
if [[ $pull -eq 1 ]]; then
  git -C "$REPO" pull --ff-only
fi
new=$(git -C "$REPO" rev-parse HEAD)
changed=""
if [[ "$old" != "$new" ]]; then
  changed=$(git -C "$REPO" diff --name-only "$old" "$new")
  log "커밋 ${old:0:7} → ${new:0:7}, 바뀐 파일 $(echo "$changed" | wc -l)개"
  echo "$changed" | sed 's/^/    /'
else
  log "새 커밋 없음 (${new:0:7})"
fi

need_backend=0; need_frontend=0; need_restart=0
grep -q '^backend/'  <<<"$changed" && need_backend=1
grep -q '^frontend/' <<<"$changed" && need_frontend=1
[[ "$force" == all || "$force" == backend  ]] && need_backend=1
[[ "$force" == all || "$force" == frontend ]] && need_frontend=1
[[ -f "$JAR" ]] || { log "jar 가 없어 백엔드를 빌드합니다"; need_backend=1; }
[[ -f "$REPO/frontend/build/index.html" ]] || { log "frontend/build 가 없어 프론트를 빌드합니다"; need_frontend=1; }

# 프론트 빌드 값(백엔드 .env 와 같은 값을 쓴다)
GCID=$(env_get GOOGLE_CLIENT_ID "")
OPEN_HOUR=$(env_get 'vori\.ai-judge\.open-hour' 20)
fe_env_hash=$(printf '%s|%s' "$GCID" "$OPEN_HOUR" | sha256sum | cut -c1-16)
if [[ "$(cat "$STATE/frontend-env" 2>/dev/null)" != "$fe_env_hash" ]]; then
  [[ -f "$STATE/frontend-env" ]] && { log "GOOGLE_CLIENT_ID/open-hour 가 바뀌어 프론트를 다시 빌드합니다"; need_frontend=1; }
fi
be_env_hash=$(sha256sum "$REPO/.env" | cut -c1-16)
if [[ "$(cat "$STATE/backend-env" 2>/dev/null)" != "$be_env_hash" ]]; then
  log ".env 가 지난 기동 이후 바뀌어 backend 를 재기동합니다"; need_restart=1
fi

others=$(grep -vE '^(backend|frontend)/' <<<"$changed" | sed '/^$/d' || true)
[[ -n "$others" ]] && log "빌드 불필요 변경 $(echo "$others" | wc -l)개 — bind volume 으로 이미 컨테이너(/app)에 반영됨"

if [[ $need_backend -eq 1 ]]; then
  log "백엔드 jar 빌드 (eclipse-temurin:17-jdk, gradle 캐시 $H/.gradle)"
  docker run --rm --name vori-build-backend --user "$VORI_UID:$VORI_GID" --memory 1536m \
    -e HOME=/tmp -e GRADLE_USER_HOME=/gradle \
    -e GRADLE_OPTS="-Xmx256m" \
    -v "$H/.gradle:/gradle" -v "$REPO:/app" -w /app/backend \
    eclipse-temurin:17-jdk \
    ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx768m bootJar -x test
  ls -l "$REPO"/backend/build/libs/
  need_restart=1
fi

if [[ $need_frontend -eq 1 ]]; then
  log "프론트 빌드 (node:22-bookworm, REACT_APP_AI_ACTIVE_FROM_HOUR=$OPEN_HOUR, GOOGLE_CLIENT_ID $([[ -n $GCID ]] && echo 설정됨 || echo 비어있음))"
  stage="$H/.frontend-stage"
  rm -rf "$stage"; mkdir -p "$stage"
  docker run --rm --name vori-build-frontend --user "$VORI_UID:$VORI_GID" --memory 1500m \
    -e HOME=/tmp -e npm_config_cache=/npm -e CI=false \
    -e NODE_OPTIONS=--max-old-space-size=1024 \
    -e GENERATE_SOURCEMAP=false \
    -e BUILD_PATH=/stage/build \
    -e REACT_APP_GOOGLE_CLIENT_ID="$GCID" \
    -e REACT_APP_AI_ACTIVE_FROM_HOUR="$OPEN_HOUR" \
    -v "$H/.npm:/npm" -v "$REPO/frontend:/w" -v "$stage:/stage" -w /w \
    node:22-bookworm sh -c 'npm ci --no-audit --no-fund && npm run build'
  [[ -f "$stage/build/index.html" ]] || { log "프론트 빌드 결과에 index.html 이 없습니다 — 기존 build 유지"; exit 1; }
  # 같은 파일시스템 안의 rename 이라 교체 순간이 아주 짧다(nginx 가 바로 새 파일을 서빙).
  [[ -d "$REPO/frontend/build" ]] && mv "$REPO/frontend/build" "$stage/old"
  mv "$stage/build" "$REPO/frontend/build"
  rm -rf "$stage"
  echo "$fe_env_hash" > "$STATE/frontend-env"
  log "프론트 교체 완료 ($(du -sh "$REPO/frontend/build" | cut -f1))"
fi
[[ -f "$STATE/frontend-env" ]] || echo "$fe_env_hash" > "$STATE/frontend-env"

if [[ $need_restart -eq 1 ]]; then
  if docker ps -q --filter name='^vori-backend$' --filter status=running | grep -q .; then
    "${COMPOSE[@]}" up -d --no-recreate
    log "vori-backend 재기동"
    "${COMPOSE[@]}" restart backend
  else
    # 컨테이너가 없거나 멈춰 있으면 만들고/켜기만 한다(막 만든 컨테이너를 곧바로 restart 하면 첫 Flyway 가 끊길 수 있다).
    log "vori 스택 기동 (up -d)"
    "${COMPOSE[@]}" up -d --no-recreate
  fi
  echo "$be_env_hash" > "$STATE/backend-env"
  log "백엔드 응답 대기 (GET /api/auth/me → 401 이 정상)"
  code=000
  for i in $(seq 1 60); do
    code=$(docker exec nginx curl -s -o /dev/null -m 3 -w '%{http_code}' http://vori-backend:8080/api/auth/me 2>/dev/null || true)
    [[ "$code" == 401 ]] && break
    sleep 3
  done
  if [[ "$code" == 401 ]]; then log "백엔드 정상 (401)"; else log "백엔드 응답 이상 (마지막 코드 $code) — docker logs vori-backend 확인"; exit 1; fi
else
  "${COMPOSE[@]}" up -d --no-recreate
  log "백엔드 재기동 불필요"
fi

log "===== deploy 끝 (${new:0:7})"
