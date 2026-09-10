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

# 우드 가구 2개를 미리 배치해 둔다. 2/3 라 발동하지 않는다 — 무대에서 발동하는
# 스터디 세트와 대비를 만드는 용도다.
FURNITURE_PRESET = ["BOOKSHELF", "DRAWER_CHEST"]

# 무대에서 살 가구. 스터디 테마는 '기록의 시작' 칭호로 잠금이 풀리므로, 무대 1번에서
# 칭호를 딴 뒤에야 살 수 있다 — 칭호 → 해금 → 구매 → 세트 발동이 하나의 사슬이 된다.
# 스터디는 required_count 가 2라 두 개면 발동한다.
FURNITURE_STAGE = ["DESKTOP_PC", "CORK_BOARD"]

# 무대 1번에서 등록할 지출. RED(z > 1.5)를 확실히 넘기면서 z_score 컬럼 범위 안이어야 한다.
# 세팅 후 평균이 4만원대라 20만원이면 z ≈ 2.0 으로 RED 가 뜬다.
STAGE_EXPENSE = 200_000

# AiInquiryService 의 인정 보상 값과 맞춘다. 바뀌면 여기도 같이 고칠 것.
RECOGNITION_STAT = 10
RECOGNITION_COIN = 100


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


def wallet(user):
    """현재 코인·펫 스탯. 인정 보상이 실제로 들어왔는지 보려면 둘 다 필요하다."""
    _, me = user.call("GET", "/api/users/me")
    _, pet = user.call("GET", "/api/pets/active")
    coins = (me.get("gameMoney") or 0) if isinstance(me, dict) else 0
    stat = pet.get("statTotal") if isinstance(pet, dict) else None
    return coins, stat


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


def wait_inquiry(user, today, seen_ids, timeout=90):
    """AI 질문은 커밋 후 비동기로 생성되고 Gemini 응답까지 기다려야 한다."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        code, rows = user.call("GET", f"/api/inquiries?date={today}")
        if isinstance(rows, list):
            fresh = [r for r in rows if r["inquiryId"] not in seen_ids]
            if fresh:
                return fresh[0]
        time.sleep(3)
    return None


def rehearse(user, user_id):
    """무대 각본을 실제로 눌러보고, 각 단계에서 무언가 터지는지 확인한다."""
    print("\n" + "=" * 62)
    print("리허설 — 무대 각본")
    print("=" * 62)
    today = datetime.date.today().isoformat()

    coin0, stat0 = wallet(user)

    print("\n[1] 큰 지출 등록 → RED → AI 가 이유를 묻는다")
    code, exp = user.call("POST", "/api/expenses", {
        "categoryId": 2, "amount": STAGE_EXPENSE, "item": "축의금",
        "spentAt": f"{today}T18:00:00"})
    check("지출 등록 200", code == 200, msg(exp) if code != 200 else "")
    check("RED 판정 ⭐", exp.get("signalFinal") == "RED",
          f"signal={exp.get('signalFinal')}, z={exp.get('zScore')}")
    check("과지출이라 절약액 음수", (exp.get("savedAmount") or 0) < 0,
          f"{exp.get('savedAmount'):,}")
    time.sleep(1.5)
    _, titles = user.call("GET", "/api/titles")
    t = {x["name"]: x for x in titles}
    check("같은 동작으로 '기록의 시작' 획득 ⭐", t.get("기록의 시작", {}).get("acquired") is True)

    inq = wait_inquiry(user, today, set())
    check("AI 질문 생성됨 ⭐", inq is not None)
    if inq is None:
        print("\n질문이 오지 않아 중단합니다."); return
    print(f"     질문: {inq['question'][:70]}")

    print("\n[2] '경조사였다' 고 답변 → 판정 완화 + 인정 보상")
    code, _ = user.call("POST", f"/api/inquiries/{inq['inquiryId']}/answer",
                        {"answerText": "친구 결혼식 축의금이라 안 낼 수가 없었어요"})
    check("답변 제출 200", code == 200, f"code={code}")
    time.sleep(1.5)

    coin1, stat1 = wallet(user)
    print(f"     코인 {coin0:,} → {coin1:,}   펫 스탯 {stat0} → {stat1}")
    check("코인 +100 ⭐", coin1 - coin0 == RECOGNITION_COIN, f"{coin1 - coin0:+}")
    check("펫 스탯 +10 ⭐", stat1 - stat0 == RECOGNITION_STAT, f"{stat1 - stat0:+}")

    code, ledger = user.call("GET", f"/api/expenses?date={today}")
    target = next((x for x in ledger if x["id"] == exp["id"]), {})
    check("신호등이 RED → GREEN 으로 완화 ⭐", target.get("signalFinal") == "GREEN",
          str(target.get("signalFinal")))

    print("\n[3] 칭호 화면")
    _, titles = user.call("GET", "/api/titles")
    acquired = [x["name"] for x in titles if x["acquired"]]
    locked = [x for x in titles if not x["acquired"]]
    check("획득 칭호가 앞쪽에 정렬됨", titles[0]["acquired"] is True)
    check("미획득에 진행률이 있음", all(x["progressPct"] is not None for x in locked))
    print(f"     획득 {len(acquired)}개 · 다음 목표 {locked[0]['name']} "
          f"{locked[0]['current']}/{locked[0]['threshold']}")

    print("\n[4] 상점 — 스터디 테마 잠금 해제 확인")
    _, themes = user.call("GET", "/api/themes")
    check("스터디 해금됨 ⭐ (1번 칭호의 결과)",
          by_name(themes).get("스터디", {}).get("unlocked") is True)
    _, products = user.call("GET", "/api/furniture/products")
    pc = {p["code"]: p for p in products}
    check("상점에서도 컴퓨터 잠금 풀림", pc["DESKTOP_PC"]["locked"] is False)

    print("\n[5] 해금된 스터디 가구 2종 구매·배치 → 세트 발동")
    for i, item in enumerate(FURNITURE_STAGE):
        code, f = user.call("POST", f"/api/furniture/buy?item={item}")
        check(f"{item} 구매 200", code == 200, msg(f) if code != 200 else "")
        if code == 200:
            user.call("PATCH", f"/api/furniture/{f['id']}/place",
                      {"positionX": 3 + i, "positionY": 0})
    _, themes = user.call("GET", "/api/themes")
    study = by_name(themes).get("스터디", {})
    check("스터디 세트 발동 ⭐", study.get("active") is True,
          f"{study.get('placedCount')}/{study.get('requiredCount')}")
    wood = by_name(themes).get("우드", {})
    check("우드는 2/3 라 미발동 (대비용)", wood.get("active") is False,
          f"{wood.get('placedCount')}/3")

    print("\n[6] 펫 분양 — 세트 보너스 반영")
    _, pet = user.call("GET", "/api/pets/active")
    stat = pet["statTotal"]
    code, released = user.call("POST", f"/api/pets/{pet['id']}/release")
    check("분양 200", code == 200, msg(released) if code != 200 else "")
    value = released.get("releaseValue")
    # 개별: 책장2.00 + 서랍장2.00 + 컴퓨터4.00 + 코르크1.50 = 9.50
    # 세트: 스터디 15.00 (우드는 2/3 라 미발동)  → 합계 24.50%
    expected = int(stat * 10 * 1.245)
    check("분양가에 개별 9.50% + 스터디 세트 15.00% 반영 ⭐", value == expected,
          f"스탯 {stat} → {value:,} 코인 (기대 {expected:,})")
    time.sleep(1.5)
    _, titles = user.call("GET", "/api/titles")
    check("'첫 분양' 획득 ⭐",
          {x["name"]: x for x in titles}.get("첫 분양", {}).get("acquired") is True)

    print("\n[7] 알 구매 → 개봉")
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
