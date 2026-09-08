# -*- coding: utf-8 -*-
"""
중간발표 시연 계정 세팅.

무대에서 누르는 버튼마다 임계값이 넘어가도록, 계정을 임계값 **직전**에서 멈춰 둔다.
각본은 docs/demo-plan.md 참조.

    python scripts/demo_seed.py              세팅만 (발표 당일 이걸 쓴다)
    python scripts/demo_seed.py --rehearse   세팅 + 무대 6단계를 실제로 눌러보는 리허설

리허설은 세팅 상태를 소모한다(지출 10번째·배치·분양·개봉을 실제로 해버린다).
발표용 계정은 --rehearse 없이 만들 것.

지출은 전부 식비 계열(categories 2~6, statType=ENERGY)을 쓴다. EMA 기준선이
statType 별로 따로 관리되므로, 다른 계열을 섞으면 절약액 계산이 어긋난다.
"""
import argparse
import datetime
import http.cookiejar
import io
import json
import sys
import time
import urllib.error
import urllib.request

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

BASE = "http://127.0.0.1:8080"
ADMIN_EMAIL, ADMIN_PW = "admin@vori.com", "1234"
PW = "Vori!2026"

# 첫 건이 기준선을 올리고, 나머지 8건이 그 평균 대비 절약으로 잡힌다.
# (categoryId, 금액, 품목)
EXPENSES = [
    (5, 180_000, "한 달 장보기"),   # 기준선 — 마트·식자재
    (6,   4_500, "편의점 도시락"),
    (3,   2_000, "아메리카노"),
    (6,   1_400, "삼각김밥"),
    (2,   5_000, "학식 정식"),
    (4,   3_200, "떡볶이 배달"),
    (3,   1_400, "자판기 커피"),
    (6,   4_500, "샌드위치"),
    (2,   3_500, "김밥천국"),
]  # 총 9건 — 10번째가 무대용("기록의 시작" 조건이 10건)

# 우드 테마 3종 중 2개만 미리 산다. 3번째(액자)를 무대에서 사서 배치하면 세트가 발동한다.
FURNITURE_PRESET = ["BOOKSHELF", "DRAWER_CHEST"]
FURNITURE_STAGE = "WALL_PICTURE"


class Session:
    def __init__(self):
        self.op = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(BASE + path, data=data, method=method)
        if data:
            req.add_header("Content-Type", "application/json")
        try:
            with self.op.open(req, timeout=90) as r:
                raw = r.read().decode()
                return r.status, (json.loads(raw) if raw.strip() else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", "replace")
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, raw[:200]
        except urllib.error.URLError as e:
            print(f"\n서버에 연결할 수 없습니다 ({BASE}). bootRun 이 떠 있는지 확인하세요.\n  {e}")
            sys.exit(1)


ok, fail = [], []


def check(label, cond, detail=""):
    (ok if cond else fail).append(label)
    print(f"  {'OK  ' if cond else 'FAIL'}  {label}" + (f"  — {detail}" if detail else ""))


def by_name(rows):
    return {r["name"]: r for r in rows} if isinstance(rows, list) else {}


def msg(res):
    return res.get("message") if isinstance(res, dict) else res


def seed(email):
    user = Session()
    code, res = user.call("POST", "/api/auth/signup", {
        "email": email, "password": PW, "nickname": "시연계정",
        "name": "시연계정", "termsAgreed": True, "privacyAgreed": True})
    if code not in (200, 201):
        print(f"계정 생성 실패: {code} {msg(res)}")
        sys.exit(1)
    user_id = res["id"]
    user.call("POST", "/api/auth/login", {"email": email, "password": PW})
    print(f"계정 생성 — id={user_id}  {email} / {PW}\n")

    # ── 지출 9건 ──
    print("지출 9건 등록")
    today = datetime.date.today()
    for i, (cat, amount, item) in enumerate(EXPENSES):
        # 하루에 몰아넣지 않고 최근 며칠에 흩어 놓는다 — 가계부 화면이 자연스러워진다.
        when = today - datetime.timedelta(days=(len(EXPENSES) - 1 - i) // 2)
        code, res = user.call("POST", "/api/expenses", {
            "categoryId": cat, "amount": amount, "item": item,
            # 시각은 두 자리로 — 한 자리면 ISO-8601 이 아니라서 400 이 난다
            "spentAt": f"{when}T{9 + i:02d}:{(i * 7) % 60:02d}:00"})
        if code != 200:
            print(f"  지출 등록 실패({item}): {code} {msg(res)}")
            sys.exit(1)
        saved = res.get("savedAmount") or 0
        print(f"  {item:14s} {amount:>8,}원   절약 {max(saved, 0):>8,}원"
              f"   신호등 {res.get('signalFinal')}")
    time.sleep(1.5)  # 칭호 이벤트(AFTER_COMMIT)가 반영될 여유

    # ── 가구 2개 구매·배치 ──
    print("\n우드 가구 2종 구매·배치 (3번째는 무대에서)")
    for i, item in enumerate(FURNITURE_PRESET):
        code, f = user.call("POST", f"/api/furniture/buy?item={item}")
        if code != 200:
            print(f"  구매 실패({item}): {code} {msg(f)}")
            sys.exit(1)
        user.call("PATCH", f"/api/furniture/{f['id']}/place",
                  {"positionX": i, "positionY": 0})
        print(f"  {f['name']} 구매·배치 ({f['price']:,} 코인)")

    # ── 펫이 성체가 아니면 어드민 치트로 보정 ──
    code, pet = user.call("GET", "/api/pets/active")
    if not isinstance(pet, dict):
        print("\n활성 펫이 없습니다. 가입 시 시작 펫이 지급되는지 확인하세요.")
        sys.exit(1)
    if pet.get("stage") != "ADULT":
        print(f"\n펫이 {pet.get('stage')}(스탯 {pet.get('statTotal')}) — 어드민 치트로 성체 보정")
        admin = Session()
        admin.call("POST", "/api/auth/login", {"email": ADMIN_EMAIL, "password": ADMIN_PW})
        code, pet = admin.call("POST", f"/api/admin/users/{user_id}/pet/grow?stage=ADULT")
        if code != 200:
            print(f"  치트 실패: {code} {msg(pet)}")
            sys.exit(1)

    return user, user_id


def verify(user):
    """무대 각본이 성립하는 상태인지 확인한다."""
    print("\n" + "=" * 62)
    print("세팅 결과 — 각본이 성립하려면 아래가 전부 OK 여야 합니다")
    print("=" * 62)

    _, me = user.call("GET", "/api/users/me")
    _, titles = user.call("GET", "/api/titles")
    _, themes = user.call("GET", "/api/themes")
    _, pet = user.call("GET", "/api/pets/active")
    _, furniture = user.call("GET", "/api/furniture")

    t = {x["name"]: x for x in titles}
    th = by_name(themes)
    coins = me.get("gameMoney") or 0
    record = t.get("기록의 시작", {})

    check("지출 9건 (10번째가 무대용)", record.get("current") == 9,
          f"{record.get('current')}/10")
    check("'기록의 시작' 미획득 ⭐", record.get("acquired") is False)
    check("'절약 새싹' 획득 (코지 해금용)", t.get("절약 새싹", {}).get("acquired") is True)
    check("'절약 고수'는 미획득 (진행률 바 시연용)",
          t.get("절약 고수", {}).get("acquired") is False,
          f"{t.get('절약 고수', {}).get('progressPct')}%")
    check("펫이 ADULT (분양 가능)", pet.get("stage") == "ADULT",
          f"스탯 {pet.get('statTotal')}")
    check("코인 충분 (액자 3,000 + 알 2,500)", coins >= 10_000, f"{coins:,} 코인")
    check("우드 2/3 — 미발동 ⭐", th.get("우드", {}).get("placedCount") == 2
          and th.get("우드", {}).get("active") is False,
          f"{th.get('우드', {}).get('placedCount')}/3")
    check("코지 해금됨", th.get("코지", {}).get("unlocked") is True)
    check("스터디 잠김 ⭐ (무대 3번의 핵심)", th.get("스터디", {}).get("unlocked") is False)
    check("배치된 가구 2개", sum(1 for f in furniture if f["placed"]) == 2)

    print(f"\n  보유 코인    {coins:,}")
    print(f"  펫           {pet.get('speciesName')} · {pet.get('stage')} · 스탯 {pet.get('statTotal')}")
    print(f"  획득 칭호    {', '.join(x['name'] for x in titles if x['acquired'])}")
    return len(fail) == 0


def rehearse(user, user_id):
    """무대 6단계를 실제로 눌러보고, 각 단계에서 무언가 터지는지 확인한다."""
    print("\n" + "=" * 62)
    print("리허설 — 무대 6단계")
    print("=" * 62)
    today = datetime.date.today().isoformat()

    print("\n[1] 지출 10번째 등록")
    code, exp = user.call("POST", "/api/expenses", {
        "categoryId": 6, "amount": 1_800, "item": "우유", "spentAt": f"{today}T18:00:00"})
    check("지출 등록 200", code == 200)
    print(f"     신호등 {exp.get('signalFinal')} · 절약 {max(exp.get('savedAmount') or 0, 0):,}원")
    time.sleep(1.5)
    _, titles = user.call("GET", "/api/titles")
    t = {x["name"]: x for x in titles}
    check("'기록의 시작' 획득 ⭐", t.get("기록의 시작", {}).get("acquired") is True)

    print("\n[2] 칭호 화면")
    acquired = [x["name"] for x in titles if x["acquired"]]
    locked = [x for x in titles if not x["acquired"]]
    check("획득 칭호가 앞쪽에 정렬됨", titles[0]["acquired"] is True)
    check("미획득에 진행률이 있음", all(x["progressPct"] is not None for x in locked))
    print(f"     획득 {len(acquired)}개 · 다음 목표 {locked[0]['name']} "
          f"{locked[0]['current']}/{locked[0]['threshold']}")

    print("\n[3] 상점 — 스터디 테마 잠금 해제 확인")
    _, themes = user.call("GET", "/api/themes")
    check("스터디 해금됨 ⭐ (1번 칭호의 결과)",
          by_name(themes).get("스터디", {}).get("unlocked") is True)
    _, products = user.call("GET", "/api/furniture/products")
    pc = {p["code"]: p for p in products}
    check("상점에서도 컴퓨터 잠금 풀림", pc["DESKTOP_PC"]["locked"] is False)

    print("\n[4] 액자 구매 → 배치 → 우드 세트 발동")
    code, f = user.call("POST", f"/api/furniture/buy?item={FURNITURE_STAGE}")
    check("액자 구매 200", code == 200, msg(f) if code != 200 else "")
    user.call("PATCH", f"/api/furniture/{f['id']}/place", {"positionX": 2, "positionY": 0})
    _, themes = user.call("GET", "/api/themes")
    wood = by_name(themes).get("우드", {})
    check("우드 세트 발동 ⭐", wood.get("active") is True, f"{wood.get('placedCount')}/3")

    print("\n[5] 펫 분양 — 세트 보너스 반영")
    _, pet = user.call("GET", "/api/pets/active")
    stat = pet["statTotal"]
    code, released = user.call("POST", f"/api/pets/{pet['id']}/release")
    check("분양 200", code == 200, msg(released) if code != 200 else "")
    value = released.get("releaseValue")
    # 개별 2.00+2.00+1.50=5.50% + 우드 세트 8.00% = 13.50%
    expected = int(stat * 10 * 1.135)
    check("분양가에 세트 13.50% 반영 ⭐", value == expected,
          f"스탯 {stat} → {value:,} 코인 (기대 {expected:,})")
    time.sleep(1.5)
    _, titles = user.call("GET", "/api/titles")
    check("'첫 분양' 획득 ⭐",
          {x["name"]: x for x in titles}.get("첫 분양", {}).get("acquired") is True)

    print("\n[6] 알 구매 → 개봉")
    code, egg = user.call("POST", "/api/eggs/buy?grade=BASIC")
    check("알 구매 200", code == 200, msg(egg) if code != 200 else "")
    code, result = user.call("POST", f"/api/eggs/{egg['id']}/open")
    check("개봉 200 (분양 후라 가능) ⭐", code == 200, msg(result) if code != 200 else "")
    if code == 200:
        new_pet = result.get("pet", {})
        print(f"     {new_pet.get('speciesName')} · {new_pet.get('tier')}등급 "
              f"· {new_pet.get('variant')} · 남은 코인 {result.get('remainGameMoney'):,}")


def main():
    ap = argparse.ArgumentParser(description="중간발표 시연 계정 세팅")
    ap.add_argument("--rehearse", action="store_true",
                    help="세팅 후 무대 6단계를 실제로 눌러본다 (세팅 상태를 소모함)")
    ap.add_argument("--email", help="계정 이메일 (기본: demo-<타임스탬프>@vori.local)")
    args = ap.parse_args()

    email = args.email or f"demo-{datetime.datetime.now():%m%d%H%M}@vori.local"
    user, user_id = seed(email)
    passed = verify(user)

    if args.rehearse:
        rehearse(user, user_id)

    print("\n" + "=" * 62)
    print(f"OK {len(ok)} / FAIL {len(fail)}")
    for f_ in fail:
        print("  실패:", f_)
    if args.rehearse:
        print("\n※ 리허설로 세팅이 소모되었습니다. 발표용 계정은 --rehearse 없이 다시 만드세요.")
    elif passed:
        print(f"\n발표 당일 이 계정으로 로그인하세요:  {email} / {PW}")
    print("=" * 62)
    sys.exit(0 if not fail else 1)


if __name__ == "__main__":
    main()
