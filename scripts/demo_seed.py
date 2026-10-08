# -*- coding: utf-8 -*-
"""
11월 시연 계정 세팅·리허설.

무대에서 누르는 버튼마다 임계값이 넘어가도록, 계정을 임계값 **직전**에서 멈춰 둔다.
각본은 docs/demo-plan.md 참조.

    python scripts/demo_seed.py
        계정·온보딩·지출·코인·가구·펫을 한 번에 세팅한다
    python scripts/demo_seed.py --rehearse
        세팅 후 시연 흐름까지 실행한다
    python scripts/demo_seed.py --rehearse --email <이메일>
        지정한 이메일로 계정을 만들고 리허설한다

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
#
# 좌표는 방 크기 대비 백분율(0~100)이다. 예전에는 (0,0)·(1,0) 을 줬는데 그러면
# 방 왼쪽 위 구석에 딱 붙어 화면에서 거의 안 보였다. 무대에서 "우드는 2/3 라 아직
# 발동하지 않았다" 를 눈으로 짚어야 하므로 방 안쪽, 펫(50,62)과 겹치지 않는 자리에 둔다.
FURNITURE_PRESET = [("BOOKSHELF", 15, 58), ("DRAWER_CHEST", 32, 62)]

# 무대에서 살 가구. 스터디 테마는 '기록의 시작' 칭호로 잠금이 풀리므로, 무대 1번에서
# 칭호를 딴 뒤에야 살 수 있다 — 칭호 → 해금 → 구매 → 세트 발동이 하나의 사슬이 된다.
# 스터디는 required_count 가 2라 두 개면 발동한다.
# 컴퓨터(DESKTOP_PC)는 모던 테마로 옮겨졌다 — 스터디는 책상·코르크 보드·빈 책상(FurnitureCatalog).
FURNITURE_STAGE = ["DESK", "CORK_BOARD"]

# 무대 1번에서 등록할 지출. RED(z > 1.5)를 확실히 넘기면서 z_score 컬럼 범위 안이어야 한다.
# 세팅 후 평균이 4만원대라 20만원이면 z ≈ 2.0 으로 RED 가 뜬다.
STAGE_EXPENSE = 200_000

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
    """현재 코인·펫 스탯. 답변·판정 직후 값이 유지되는지 비교한다."""
    _, me = user.call("GET", "/api/users/me")
    _, pet = user.call("GET", "/api/pets/active")
    coins = (me.get("gameMoney") or 0) if isinstance(me, dict) else 0
    stat = pet.get("statTotal") if isinstance(pet, dict) else None
    return coins, stat


def msg(res):
    return res.get("message") if isinstance(res, dict) else res


def complete_onboarding(user):
    """가입 직후 온보딩 설문을 끝낸다. 안 하면 로그인 때 설문 화면으로 튕긴다(App.js OnboardingGate).

    한 끼 식비는 UNKNOWN 으로 둔다 — 식비(ENERGY) 기준선은 씨딩하지 않아야 각본 1·2번의
    z=2.263·코인 63,537 같은 실측값이 그대로 유지된다. 월 수입은 필수라 넣지만 쇼핑·문화·생활
    타입에만 영향을 주고 각본은 전부 식비라 무관하다.
    """
    code, res = user.call("POST", "/api/onboarding/spending-profile", {
        "monthlyIncome": 800000,
        "monthlyBudgetBand": "UNKNOWN", "mealCostBand": "UNKNOWN",
        "primarySpendArea": "FOOD_CAFE", "spendingHabit": "UNKNOWN", "monthlyGoal": "GROW_PET"})
    if code != 200:
        print(f"온보딩 설문 저장 실패: {code} {msg(res)}")
        sys.exit(1)
    code, res = user.call("POST", "/api/onboarding/complete")
    if code not in (200, 204):
        print(f"온보딩 완료 처리 실패: {code} {msg(res)}")
        sys.exit(1)


def required_furniture_coins(user):
    """상품 API의 가격으로 준비·무대 가구에 필요한 코인을 합산한다."""
    code, products = user.call("GET", "/api/furniture/products")
    if code != 200 or not isinstance(products, list):
        print(f"가구 상품 조회 실패: {code} {msg(products)}")
        sys.exit(1)

    by_code = {p.get("code"): p for p in products if isinstance(p, dict)}
    items = [item for item, _, _ in FURNITURE_PRESET] + FURNITURE_STAGE
    missing = [item for item in items if item not in by_code]
    if missing:
        print(f"가구 상품 목록에 항목이 없습니다: {', '.join(missing)}")
        sys.exit(1)
    try:
        total = sum(int(by_code[item]["price"]) for item in items)
        stage_total = sum(int(by_code[item]["price"]) for item in FURNITURE_STAGE)
    except (KeyError, TypeError, ValueError):
        print("가구 상품 목록에서 가격을 읽을 수 없습니다.")
        sys.exit(1)
    return total, stage_total


def admin_session():
    """관리자 세션은 지급과 펫 레벨 치트에서 공통으로 쓴다."""
    admin = Session()
    code, res = admin.call("POST", "/api/auth/login", {"email": ADMIN_EMAIL, "password": ADMIN_PW})
    if code != 200:
        print(f"관리자 로그인 실패: {code} {msg(res)}")
        sys.exit(1)
    return admin


def seed(email):
    """계정·온보딩·지출 뒤 부족한 코인만 지급하고 시연 상태를 만든다."""
    user = Session()
    code, res = user.call("POST", "/api/auth/signup", {
        "email": email, "password": PW, "nickname": "시연계정",
        "name": "시연계정", "termsAgreed": True, "privacyAgreed": True})
    if code not in (200, 201):
        print(f"계정 생성 실패: {code} {msg(res)}")
        sys.exit(1)
    user_id = res["id"]
    code, res = user.call("POST", "/api/auth/login", {"email": email, "password": PW})
    if code != 200:
        print(f"로그인 실패: {code} {msg(res)}")
        sys.exit(1)
    complete_onboarding(user)
    print(f"계정 생성 — id={user_id}  {email} / {PW}\n")

    # ── 지출 9건 ──
    print("지출 9건 등록")
    yesterday = datetime.date.today() - datetime.timedelta(days=1)
    for i, (cat, amount, item) in enumerate(EXPENSES):
        # 최근 며칠에 나누되 모든 지출을 어제와 그 이전에 둔다.
        when = yesterday - datetime.timedelta(days=(len(EXPENSES) - 1 - i) // 2)
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

    required_coins, stage_coins = required_furniture_coins(user)
    code, me = user.call("GET", "/api/users/me")
    if code != 200 or not isinstance(me, dict):
        print(f"보유 코인 조회 실패: {code} {msg(me)}")
        sys.exit(1)
    user_id = me.get("id")
    if user_id is None:
        print("계정 정보를 읽을 수 없습니다.")
        sys.exit(1)
    coins = me.get("gameMoney")
    if not isinstance(coins, int):
        print("보유 코인 응답을 읽을 수 없습니다.")
        sys.exit(1)
    granted = max(0, required_coins - coins)
    admin = None
    if granted:
        admin = admin_session()
        code, result = admin.call("POST", f"/api/admin/users/{user_id}/coins?amount={granted}")
        if code != 200 or not isinstance(result, dict):
            print(f"관리자 코인 지급 실패: {code} {msg(result)}")
            sys.exit(1)
        if result.get("granted") != granted:
            print(f"관리자 지급액이 요청과 다릅니다: 요청 {granted:,} / 응답 {result.get('granted')}")
            sys.exit(1)
        coins = result.get("gameMoney")
        if not isinstance(coins, int):
            print("지급 후 잔액 응답을 읽을 수 없습니다.")
            sys.exit(1)
    print(f"코인 지급 — 지급 {granted:,} / 지급 후 잔액 {coins:,} / 필요 {required_coins:,}")

    # ── 가구 2개 구매·배치 ──
    print("\n우드 가구 2종 구매·배치 (3번째는 무대에서)")
    for item, x, y in FURNITURE_PRESET:
        code, f = user.call("POST", f"/api/furniture/buy?item={item}")
        if code != 200:
            print(f"  구매 실패({item}): {code} {msg(f)}")
            sys.exit(1)
        user.call("PATCH", f"/api/furniture/{f['id']}/place",
                  {"positionX": x, "positionY": y})
        print(f"  {f['name']} 구매·배치 ({f['price']:,} 코인)")

    # ── 펫이 30레벨이 아니면 분양 조건에 맞춰 어드민 치트로 보정 ──
    code, pet = user.call("GET", "/api/pets/active")
    if not isinstance(pet, dict):
        print("\n활성 펫이 없습니다. 가입 시 시작 펫이 지급되는지 확인하세요.")
        sys.exit(1)
    if pet.get("level", 0) < pet.get("maxLevel", 30):
        print(f"\n펫이 {pet.get('level')}레벨 — 분양을 위해 30레벨로 보정")
        admin = admin or admin_session()
        code, pet = admin.call("POST", f"/api/admin/users/{user_id}/pet/grow?level=30")
        if code != 200:
            print(f"  치트 실패: {code} {msg(pet)}")
            sys.exit(1)

    return user, user_id, stage_coins


def verify(user, stage_coins):
    """무대 각본이 성립하는 상태인지 확인한다."""
    print("\n" + "=" * 62)
    print("세팅 결과 — 각본이 성립하려면 아래가 전부 OK 여야 합니다")
    print("=" * 62)

    _, me = user.call("GET", "/api/users/me")
    _, titles = user.call("GET", "/api/achievements")
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
    check("펫 30레벨 (분양 가능)", pet.get("level") == pet.get("maxLevel"),
          f"레벨 {pet.get('level')}/{pet.get('maxLevel')}")
    check("코인 충분 (무대 스터디 가구 2종)", coins >= stage_coins,
          f"보유 {coins:,} / 필요 {stage_coins:,} 코인")
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
    """시연 흐름을 실제로 실행하고 각 단계의 응답과 상태를 확인한다."""
    print("\n" + "=" * 62)
    print("리허설 — 시연 각본")
    print("=" * 62)
    today = datetime.date.today().isoformat()

    print("\n[1] 큰 지출 등록 → RED → AI 가 이유를 묻는다")
    # 품목명을 "결혼식 축의금" 으로 쓴다. 무대에서 사람이 화면에 타이핑할 문구와 같아야 한다 —
    # "축의금" 만 치면 자동분류가 고정비·공과금(ENDURANCE)으로 보내고, 그쪽은 표본이 0이라
    # sample_count < 5 규칙에 걸려 무조건 GREEN 이 된다(RED 가 아니면 AI 질문도 칭호도 없다).
    # 여기서는 categoryId 를 직접 주므로 그 문제가 드러나지 않는다. docs/demo-plan.md 참조.
    code, exp = user.call("POST", "/api/expenses", {
        "categoryId": 2, "amount": STAGE_EXPENSE, "item": "결혼식 축의금",
        "spentAt": f"{today}T18:00:00"})
    check("지출 등록 200", code == 200, msg(exp) if code != 200 else "")
    check("RED 판정 ⭐", exp.get("signalFinal") == "RED",
          f"signal={exp.get('signalFinal')}, z={exp.get('zScore')}")
    check("과지출이라 절약액 음수", (exp.get("savedAmount") or 0) < 0,
          f"{exp.get('savedAmount'):,}")
    time.sleep(1.5)
    _, titles = user.call("GET", "/api/achievements")
    t = {x["name"]: x for x in titles}
    check("같은 동작으로 '기록의 시작' 획득 ⭐", t.get("기록의 시작", {}).get("acquired") is True)

    inq = wait_inquiry(user, today, set())
    check("AI 질문 생성됨 ⭐", inq is not None)
    if inq is None:
        print("\n질문이 오지 않아 중단합니다."); return
    print(f"     질문: {inq['question'][:70]}")

    print("\n[2] 판정하기 — 1차 판정")
    code, first = user.call("POST", f"/api/daily-judgments?date={today}")
    if code == 403:
        check("1차 판정 시각 제한(HTTP 403)", False, msg(first))
        print("     서버를 --vori.ai-judge.open-hour=0 으로 띄운 뒤 다시 실행하세요.")
        return
    check("1차 판정 요청 200", code == 200, msg(first) if code != 200 else "")
    if code != 200:
        return
    first = first if isinstance(first, dict) else {}
    first_status = first.get("status")
    check("1차 판정 PENDING", first_status == "PENDING", f"status={first_status}")
    if first_status != "PENDING":
        return
    initial_groups = first.get("initialGroupJudgments")
    initial_groups = initial_groups if isinstance(initial_groups, dict) else {}
    initial_energy = initial_groups.get("ENERGY")
    initial_energy_signal = initial_energy.get("signal") if isinstance(initial_energy, dict) else None
    check("1차 ENERGY(식비) 그룹 신호 확인", initial_energy_signal is not None,
          f"signal={initial_energy_signal}")
    print(f"     1차 ENERGY(식비) 그룹 신호: {initial_energy_signal or '없음'}")

    print("\n[3] 사유 답변 — CEREMONY 칩")
    coin_before_answer, stat_before_answer = wallet(user)
    code, _ = user.call("POST", f"/api/inquiries/{inq['inquiryId']}/answer",
                        {"reasonCategory": "CEREMONY"})
    check("사유 칩 제출 200", code == 200, f"code={code}")
    if code != 200:
        return
    time.sleep(1.5)

    coin_after_answer, stat_after_answer = wallet(user)
    print(f"     답변 전후 코인 {coin_before_answer:,} → {coin_after_answer:,}"
          f"   펫 스탯 {stat_before_answer} → {stat_after_answer}")
    check("답변 직후 코인 유지", coin_after_answer == coin_before_answer,
          f"{coin_before_answer:,} → {coin_after_answer:,}")
    check("답변 직후 펫 스탯 유지", stat_after_answer == stat_before_answer,
          f"{stat_before_answer} → {stat_after_answer}")

    code, ledger = user.call("GET", f"/api/expenses?date={today}")
    check("답변 후 가계부 조회 200", code == 200, msg(ledger) if code != 200 else "")
    rows = ledger if isinstance(ledger, list) else []
    target = next((x for x in rows if x["id"] == exp["id"]), {})
    check("사유가 인정돼 신호등이 GREEN", target.get("signalFinal") == "GREEN",
          str(target.get("signalFinal")))

    print("\n[4] 최종 판정")
    coin_before_finalize, stat_before_finalize = wallet(user)
    code, final = user.call("POST", f"/api/daily-judgments/finalize?date={today}")
    check("최종 판정 요청 200", code == 200, msg(final) if code != 200 else "")
    if code != 200:
        return
    final = final if isinstance(final, dict) else {}
    final_status = final.get("status")
    check("최종 상태 FINALIZED", final_status == "FINALIZED", f"status={final_status}")
    if final_status != "FINALIZED":
        return
    check("자정 전 보상 미지급", final.get("rewarded") is False,
          f"rewarded={final.get('rewarded')}")
    final_groups = final.get("groupJudgments")
    final_groups = final_groups if isinstance(final_groups, dict) else {}
    final_energy = final_groups.get("ENERGY")
    final_energy_signal = final_energy.get("signal") if isinstance(final_energy, dict) else None
    if initial_energy_signal == "RED":
        check("ENERGY 신호 RED → GREEN 완화", final_energy_signal == "GREEN",
              f"signal={final_energy_signal}")
    else:
        print(f"     1차 ENERGY 그룹이 RED가 아니었습니다: {initial_energy_signal or '없음'}")

    coin_after_finalize, stat_after_finalize = wallet(user)
    print(f"     최종 판정 전후 코인 {coin_before_finalize:,} → {coin_after_finalize:,}"
          f"   펫 스탯 {stat_before_finalize} → {stat_after_finalize}")
    check("최종 판정 직후 코인 유지", coin_after_finalize == coin_before_finalize,
          f"{coin_before_finalize:,} → {coin_after_finalize:,}")
    check("최종 판정 직후 펫 스탯 유지", stat_after_finalize == stat_before_finalize,
          f"{stat_before_finalize} → {stat_after_finalize}")
    print("     보상은 오늘 자정에 들어옵니다.")

    print("\n[5] 칭호 화면")
    _, titles = user.call("GET", "/api/achievements")
    acquired = [x["name"] for x in titles if x["acquired"]]
    locked = [x for x in titles if not x["acquired"]]
    check("획득 칭호가 앞쪽에 정렬됨", titles[0]["acquired"] is True)
    check("미획득에 진행률이 있음", all(x["progressPct"] is not None for x in locked))
    print(f"     획득 {len(acquired)}개 · 다음 목표 {locked[0]['name']} "
          f"{locked[0]['current']}/{locked[0]['threshold']}")

    print("\n[6] 상점 — 스터디 테마 잠금 해제 확인")
    _, themes = user.call("GET", "/api/themes")
    check("스터디 해금됨 ⭐ (1번 칭호의 결과)",
          by_name(themes).get("스터디", {}).get("unlocked") is True)
    _, products = user.call("GET", "/api/furniture/products")
    shop = {p["code"]: p for p in products}
    check("상점에서도 스터디 가구 잠금 풀림", shop[FURNITURE_STAGE[0]]["locked"] is False)

    print("\n[7] 해금된 스터디 가구 2종 구매·배치 → 세트 발동")
    for i, item in enumerate(FURNITURE_STAGE):
        code, f = user.call("POST", f"/api/furniture/buy?item={item}")
        check(f"{item} 구매 200", code == 200, msg(f) if code != 200 else "")
        if code == 200:
            user.call("PATCH", f"/api/furniture/{f['id']}/place",
                      {"positionX": 68 + i * 12, "positionY": 40 + i * 20})
    _, themes = user.call("GET", "/api/themes")
    study = by_name(themes).get("스터디", {})
    check("스터디 세트 발동 ⭐", study.get("active") is True,
          f"{study.get('placedCount')}/{study.get('requiredCount')}")
    wood = by_name(themes).get("우드", {})
    check("우드는 2/3 라 미발동 (대비용)", wood.get("active") is False,
          f"{wood.get('placedCount')}/3")

    print("\n[8] 펫 분양 — 세트 보너스 반영")
    _, pet = user.call("GET", "/api/pets/active")
    stat = pet["statTotal"]
    code, released = user.call("POST", f"/api/pets/{pet['id']}/release")
    check("분양 200", code == 200, msg(released) if code != 200 else "")
    value = released.get("releaseValue")
    # 개별: 책장2.00 + 서랍장2.00 + 책상2.00 + 코르크1.50 = 7.50
    # 세트: 스터디 15.00 (우드는 2/3 라 미발동)  → 합계 22.50%
    expected = int(stat * 10 * 1.225)
    check("분양가에 개별 7.50% + 스터디 세트 15.00% 반영 ⭐", value == expected,
          f"스탯 {stat} → {value:,} 코인 (기대 {expected:,})")
    time.sleep(1.5)
    _, titles = user.call("GET", "/api/achievements")
    check("'첫 분양' 획득 ⭐",
          {x["name"]: x for x in titles}.get("첫 분양", {}).get("acquired") is True)

    print("\n[9] 알 구매 → 개봉")
    code, egg = user.call("POST", "/api/eggs/buy?grade=BASIC")
    check("알 구매 200", code == 200, msg(egg) if code != 200 else "")
    code, result = user.call("POST", f"/api/eggs/{egg['id']}/open")
    check("개봉 200 (분양 후라 가능) ⭐", code == 200, msg(result) if code != 200 else "")
    if code == 200:
        new_pet = result.get("pet", {})
        print(f"     {new_pet.get('speciesName')} · {new_pet.get('tier')}등급 "
              f"· {new_pet.get('variant')} · 남은 코인 {result.get('remainGameMoney'):,}")


def main():
    ap = argparse.ArgumentParser(description="시연 계정을 한 번에 세팅하고 선택적으로 리허설한다")
    ap.add_argument("--rehearse", action="store_true",
                    help="세팅 후 시연 흐름을 실행한다 (세팅 상태를 소모함)")
    ap.add_argument("--email", help="계정 이메일 (기본: demo-<타임스탬프>@vori.local)")
    args = ap.parse_args()

    email = args.email or f"demo-{datetime.datetime.now():%m%d%H%M}@vori.local"
    user, user_id, stage_coins = seed(email)
    passed = verify(user, stage_coins)

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
