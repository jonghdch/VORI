#!/usr/bin/env bash
# VORI 배포 SSH 진입점 — GitHub Actions 배포 키가 부르는 래퍼(vori 권한으로 실행).
#
# vori 의 /data/home2/vori.worldpick.co.kr/.ssh/authorized_keys 에 아래처럼 등록돼 있다(한 줄):
#   command="/data/home2/vori.worldpick.co.kr/deploy-ssh-entry.sh",no-pty,no-port-forwarding,no-X11-forwarding,no-agent-forwarding ssh-ed25519 AAAA... github-deploy-vori
#
# 이 키로 접속하면 클라이언트가 보낸 명령은 실행되지 않고 이 스크립트만 돈다. 보낸 문자열
# (SSH_ORIGINAL_COMMAND)은 셸로 해석하지 않고 공백으로만 쪼개 아래 허용 인자인지 검사한다.
#   --force-build all|backend|frontend
#   --no-pull
# 그 밖의 인자가 하나라도 있으면 logs/deploy.log 에 거부 기록을 남기고 exit 1 (배포하지 않음).
# (서버에서 직접 시험할 때는 인자로 줘도 같은 검사를 한다: deploy-ssh-entry.sh --no-pull)
#
# 서버 위치: /data/home2/vori.worldpick.co.kr/deploy-ssh-entry.sh (vori:vori 750)
set -euo pipefail
set -f   # glob 확장 금지

H=/data/home2/vori.worldpick.co.kr
DEPLOY="$H/deploy.sh"
LOG="$H/logs/deploy.log"
who="$(id -un)"

# forced command 로 불리면 인자는 없고 SSH_ORIGINAL_COMMAND 에 클라이언트가 보낸 문자열이 있다.
if (( $# == 0 )) && [[ -n "${SSH_ORIGINAL_COMMAND:-}" ]]; then
  read -r -a words <<<"$SSH_ORIGINAL_COMMAND" || true
else
  words=("$@")
fi

reject() {
  local msg="[$(date '+%F %T')] deploy-ssh-entry 거부 (user=$who): $1"
  echo "$msg" >&2
  echo "$msg" >>"$LOG" 2>/dev/null || true
  exit 1
}

(( ${#words[@]} <= 3 )) || reject "인자가 너무 많습니다 (${#words[@]}개)"
args=(); seen_force=0; seen_nopull=0
i=0
while (( i < ${#words[@]} )); do
  w="${words[$i]}"
  case "$w" in
    --force-build)
      (( seen_force == 0 )) || reject "--force-build 중복"
      next="${words[$((i+1))]:-}"
      [[ "$next" =~ ^(all|backend|frontend)$ ]] || reject "--force-build 값은 all|backend|frontend 중 하나여야 합니다"
      args+=(--force-build "$next"); seen_force=1; i=$((i+1)) ;;
    --no-pull)
      (( seen_nopull == 0 )) || reject "--no-pull 중복"
      args+=(--no-pull); seen_nopull=1 ;;
    *) reject "허용되지 않은 인자: $(printf '%q' "$w" | cut -c1-80)" ;;
  esac
  i=$((i+1))
done

echo "deploy-ssh-entry: user=$who args=${args[*]:-(없음)}"
exec "$DEPLOY" "${args[@]}"
