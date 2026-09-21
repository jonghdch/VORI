# -*- coding: utf-8 -*-
"""장착 중인 칭호 회수 — 어드민 API. bootRun 실행 중일 때."""
import json
import sys
import urllib.error
import urllib.request
import http.cookiejar

BASE = "http://127.0.0.1:8080/api"
ADMIN_EMAIL, ADMIN_PW = "admin@vori.com", "1234"


def main():
    op = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
    )

    def call(method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(BASE + path, data=data, method=method)
        if data:
            req.add_header("Content-Type", "application/json")
        try:
            with op.open(req, timeout=15) as r:
                raw = r.read().decode()
                return r.status, (json.loads(raw) if raw.strip() else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, raw[:300]

    code, _ = call("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": ADMIN_PW})
    if code != 200:
        print(f"어드민 로그인 실패: {code}")
        sys.exit(1)

    code, res = call("GET", "/admin/users?page=0&size=100")
    if code != 200:
        print(f"유저 목록 실패: {code} {res}")
        sys.exit(1)

    users = res.get("content") or []
    targets = [u for u in users if u.get("role") == "USER"]
    if not targets:
        print("일반 USER 계정이 없습니다.")
        sys.exit(0)

    for u in sorted(targets, key=lambda x: x["id"]):
        uid = u["id"]
        code, res = call("POST", f"/admin/users/{uid}/titles/revoke-equipped")
        if code == 204:
            print(f"회수 완료 — id={uid} {u.get('email')}")
        elif code == 404:
            print(f"건너뜀(엔드포인트 없음) — 백엔드 재시작 후 다시 실행하세요.")
            sys.exit(1)
        else:
            print(f"id={uid} status={code} {res}")


if __name__ == "__main__":
    main()
