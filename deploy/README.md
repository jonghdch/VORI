# deploy/ — 운영 배포 파일

`https://vori.worldpick.co.kr` 운영 서버(`ssh zzemsoft`, 도커)의 설정 파일 사본이다. 절차·점검은 [`docs/deploy-checklist.md`](../docs/deploy-checklist.md).

| 파일 | 서버의 실제 위치 | 비고 |
|---|---|---|
| [`docker/docker-compose.yml`](docker/docker-compose.yml) | `/data/home2/vori.worldpick.co.kr/docker-compose.yml` | compose 프로젝트 `vori` (mysql + backend) |
| [`docker/deploy.sh`](docker/deploy.sh) | `/data/home2/vori.worldpick.co.kr/deploy.sh` | 일상 배포 스크립트 (**vori 로 실행**, root 면 거부) |
| [`docker/deploy-ssh-entry.sh`](docker/deploy-ssh-entry.sh) | `/data/home2/vori.worldpick.co.kr/deploy-ssh-entry.sh` | GitHub Actions 배포 키의 forced command. `SSH_ORIGINAL_COMMAND` 에서 `--force-build all\|backend\|frontend`·`--no-pull` 만 deploy.sh 로 넘기고 그 밖의 인자는 거부(exit 1, `logs/deploy.log` 기록) |
| [`docker/compose.env.example`](docker/compose.env.example) | 같은 폴더의 `.env` (600) | compose 보간용 `MYSQL_ROOT_PASSWORD`·`DB_PASSWORD` — 키 이름만 |
| [`docker/app.env.example`](docker/app.env.example) | `.../VORI/.env` (vori 소유, 600) | 앱 설정 — 키 이름과 설명만 |
| [`nginx/vori.worldpick.co.kr.conf`](nginx/vori.worldpick.co.kr.conf) | `/home/dockers/nginx/conf.d/vori.worldpick.co.kr.conf` | 기존 공용 nginx 컨테이너의 사이트 설정 |

서버의 이 디렉터리 아래는 **전부 `vori:vori` 소유**이고 deploy.sh·컨테이너(vori-backend·vori-mysql)도 vori(1027) 권한으로 돈다. root 로 파일을 만들지 않는다. vori 는 `docker` 그룹이라 docker 를 쓸 수 있다 — docker 그룹은 사실상 root 동급 권한이므로 vori 계정·배포 키를 root 처럼 다룬다. GitHub Actions 자동 배포 구성·secret·키 재발급은 [`docs/deploy-checklist.md`](../docs/deploy-checklist.md) 2-3절.

비밀값(`.env` 실제 값)은 저장소에 올리지 않는다. `.gitignore` 가 `deploy/docker/*.env` 를 막고 `*.env.example` 만 허용한다.

## 규칙: 레포와 서버를 같게 유지한다

서버의 실제 파일이 원본이다. 바꿀 때는 **레포를 고치고 서버에 복사하거나(권장), 서버를 고치고 레포로 복사해 둘을 같게 유지**한다. 한쪽만 바꿔 두면 다음 사람이 모르고 되돌린다. 확인:

```bash
S=/data/home2/vori.worldpick.co.kr
diff <(ssh zzemsoft cat $S/docker-compose.yml) deploy/docker/docker-compose.yml
diff <(ssh zzemsoft cat $S/deploy.sh) deploy/docker/deploy.sh
diff <(ssh zzemsoft cat $S/deploy-ssh-entry.sh) deploy/docker/deploy-ssh-entry.sh
diff <(ssh zzemsoft cat /home/dockers/nginx/conf.d/vori.worldpick.co.kr.conf) deploy/nginx/vori.worldpick.co.kr.conf
```

## 서버에 반영하는 명령

```bash
S=/data/home2/vori.worldpick.co.kr

# 서버 파일은 vori 소유로 유지한다 — scp 는 vori 로 들어갈 수 없으니(배포 키는 forced command) root 로 /tmp 에 올린 뒤 vori 로 옮긴다.
put() { scp "$1" zzemsoft:/tmp/vori-put && ssh zzemsoft "chown vori:vori /tmp/vori-put && runuser -u vori -- install -m $3 /tmp/vori-put $S/$2 && rm -f /tmp/vori-put"; }

# compose 수정 — 복사 후 변경된 서비스만 다시 만든다
put deploy/docker/docker-compose.yml docker-compose.yml 640
ssh zzemsoft "cd $S && runuser -u vori -- docker compose -p vori up -d"

# deploy.sh·deploy-ssh-entry.sh 수정
put deploy/docker/deploy.sh deploy.sh 750
put deploy/docker/deploy-ssh-entry.sh deploy-ssh-entry.sh 750

# nginx 수정 — 복사 후 문법 검사가 통과한 때만 reload (restart 금지: 다른 사이트도 이 컨테이너를 쓴다)
scp deploy/nginx/vori.worldpick.co.kr.conf zzemsoft:/home/dockers/nginx/conf.d/vori.worldpick.co.kr.conf
ssh zzemsoft "docker exec nginx nginx -t && docker exec nginx nginx -s reload"
```

앱 설정(`VORI/.env`)은 서버에서만 고치고, 고친 뒤 `deploy.sh` 를 한 번 실행한다(`ssh zzemsoft 'cd /data/home2/vori.worldpick.co.kr && runuser -u vori -- ./deploy.sh'`). 새 설정 키를 만들면 `app.env.example`(와 루트 `.env.example`)에 키 이름을 같이 적는다.
