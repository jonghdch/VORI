"""10명 페르소나 × 6개월 시뮬레이션 + 방식 비교.

각 페르소나마다:
  - 180일 소비 데이터 생성 (개인 패턴 반영)
  - A(제외) / B / C / D / E 판정
  - 결과 집계

목적: 직업·수입·라이프스타일이 다를 때 어떤 방식이 일관되고 합리적인지 판별
"""
from __future__ import annotations

import http.client
import json
import random
import time
from collections import Counter, defaultdict
from datetime import date, timedelta
from pathlib import Path

from personas import PERSONAS, Persona

BASE_HOST = "127.0.0.1"
BASE_PORT = 8000
OUT_PATH = Path(__file__).parent / "simulation_multi_result.json"

SEED_BASE = 1000

# 지속 연결 하나 재사용 (ephemeral port 소진 방지)
_conn: http.client.HTTPConnection | None = None


def _get_conn() -> http.client.HTTPConnection:
    global _conn
    if _conn is None:
        _conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=60)
    return _conn


def _request(method: str, path: str, body: dict | None = None) -> dict:
    global _conn
    for attempt in range(3):
        try:
            conn = _get_conn()
            headers = {"Content-Type": "application/json"} if body is not None else {}
            data = json.dumps(body).encode("utf-8") if body is not None else None
            conn.request(method, path, body=data, headers=headers)
            resp = conn.getresponse()
            text = resp.read().decode("utf-8", errors="ignore")
            if resp.status >= 400:
                raise RuntimeError(f"{resp.status}: {text[:200]}")
            return json.loads(text) if text else {}
        except (http.client.HTTPException, OSError, ConnectionError) as e:
            # 재연결
            try:
                if _conn:
                    _conn.close()
            except Exception:
                pass
            _conn = None
            if attempt == 2:
                raise
            time.sleep(0.5)
    return {}


def _scale(amt: int, persona: Persona) -> int:
    return int(amt * persona.meal_scale)


def generate_expenses(persona: Persona, days: int = 180, seed: int = 0) -> list[dict]:
    """페르소나 특성 반영한 180일 소비 생성."""
    rnd = random.Random(seed)
    start = date(2025, 10, 13)
    expenses = []

    meals_morning = [("편의점 삼각김밥", 2500), ("편의점 샌드위치", 3500), ("카페 아침 세트", 6000)]
    meals_lunch = [("학식", 5000), ("김밥천국 점심", 9000), ("편의점 도시락", 5000), ("동네 한식", 9500)]
    meals_dinner = [("편의점 도시락 저녁", 5500), ("프랜차이즈 저녁", 9000), ("동네 식당", 10000)]
    dinner_out = [("친구와 저녁 외식", 22000), ("삼겹살 회식", 35000), ("파스타집", 16000), ("치킨 회식", 18000)]
    cafes = [("아메리카노", 4500), ("라떼", 5500), ("디저트 카페", 8000)]
    shopping_small = [("양말", 5000), ("생필품", 4000), ("기초 화장품", 25000), ("계절 티셔츠", 18000)]
    shopping_big = [("세일 코트", 85000), ("운동화 교체", 72000), ("명품 충동", 250000)]
    leisure_small = [("영화 티켓", 13000), ("서점 신간", 18000), ("전시회", 15000), ("방탈출", 22000)]
    leisure_big = [("콘서트 티켓", 80000), ("뮤지컬", 95000), ("주말 여행", 100000)]

    # special 이벤트 (페르소나 공통)
    specials = [
        ("친구 결혼식 축의금", 100_000, "문화여가", "점심", "친한 친구 결혼식"),
        ("응급실 택시비", 40_000, "생활고정", "새벽", "가족 응급 상황"),
        ("자격증 교재", 45_000, "문화여가", "점심", "자기투자"),
        ("가족 생일 외식", 80_000, "식비", "저녁", "가족 생일"),
        ("시험기간 배달", 28_000, "식비", "새벽", None),
    ]
    special_days = sorted(rnd.sample(range(15, days - 5), len(specials)))
    special_map = {d: e for d, e in zip(special_days, specials)}

    for d in range(days):
        cur = start + timedelta(days=d)
        if d in special_map:
            desc, amt, cat, tod, reason = special_map[d]
            expenses.append({"date": cur.isoformat(), "description": desc, "amount": int(amt * persona.meal_scale), "category": cat, "time_of_day": tod, "reason": reason})

        # 식비 — 아침(50%), 점심(100%), 저녁(85%)
        if rnd.random() < 0.5:
            d_, amt = rnd.choice(meals_morning)
            expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "식비", "time_of_day": "아침", "reason": None})
        d_, amt = rnd.choice(meals_lunch)
        expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "식비", "time_of_day": "점심", "reason": None})
        if rnd.random() < 0.85:
            if rnd.random() < persona.dine_out_prob:
                d_, amt = rnd.choice(dinner_out)
            else:
                d_, amt = rnd.choice(meals_dinner)
            expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "식비", "time_of_day": "저녁", "reason": None})

        # 카페
        if rnd.random() < persona.cafe_prob:
            d_, amt = rnd.choice(cafes)
            expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "식비", "time_of_day": "아침", "reason": None})

        # 쇼핑
        if rnd.random() < persona.shopping_big_prob:
            d_, amt = rnd.choice(shopping_big)
            expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "쇼핑뷰티", "time_of_day": "저녁", "reason": None})
        elif rnd.random() < persona.shopping_prob:
            d_, amt = rnd.choice(shopping_small)
            expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "쇼핑뷰티", "time_of_day": rnd.choice(["점심", "저녁"]), "reason": None})

        # 여가
        if rnd.random() < persona.leisure_big_prob:
            d_, amt = rnd.choice(leisure_big)
            expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "문화여가", "time_of_day": "저녁", "reason": None})
        elif rnd.random() < persona.leisure_prob:
            d_, amt = rnd.choice(leisure_small)
            expenses.append({"date": cur.isoformat(), "description": d_, "amount": _scale(amt, persona), "category": "문화여가", "time_of_day": rnd.choice(["점심", "저녁"]), "reason": None})

        # 고정비
        for fdesc, famt, fday in persona.fixed_costs:
            if cur.day == fday:
                expenses.append({"date": cur.isoformat(), "description": fdesc, "amount": famt, "category": "생활고정", "time_of_day": "아침", "reason": None})

    return expenses


def post(path, body):
    return _request("POST", path, body)


def delete(path):
    _request("DELETE", path)


def judge(method: str, exp: dict, context: dict) -> dict:
    body = {
        "approach": method,
        "expense": {
            "description": exp["description"],
            "amount": exp["amount"],
            "category": exp["category"],
            "time_of_day": exp["time_of_day"],
            "date": exp.get("date"),
        },
        "user_context": context,
        "reason": exp.get("reason"),
    }
    return post("/judge", body)


def simulate_persona(persona: Persona, seed_idx: int) -> dict:
    delete("/adaptive/state")
    delete("/history")

    expenses = generate_expenses(persona, days=180, seed=SEED_BASE + seed_idx)
    ctx = persona.context

    sig_by_method = defaultdict(Counter)  # method -> signal counter
    delta_by_method = defaultdict(int)
    reds_by_method_cat = defaultdict(lambda: defaultdict(int))

    t0 = time.time()
    for exp in expenses:
        for m in ("B", "C", "D", "E"):
            try:
                r = judge(m, exp, ctx)
                sig_by_method[m][r["signal"]] += 1
                delta_by_method[m] += r["stat_delta"]
                if r["signal"] == "red":
                    reds_by_method_cat[m][exp["category"]] += 1
            except Exception as e:
                sig_by_method[m]["error"] += 1

    elapsed = time.time() - t0
    total = len(expenses)

    # E final state
    try:
        ema_state = _request("GET", "/adaptive/state")
    except Exception:
        ema_state = None

    return {
        "name": persona.name,
        "job": persona.job,
        "income": persona.monthly_income,
        "assets": persona.total_assets,
        "category_avg": persona.category_avg,
        "total_expenses": total,
        "elapsed_seconds": round(elapsed, 2),
        "signals": {m: dict(sig_by_method[m]) for m in ("B", "C", "D", "E")},
        "cumulative_delta": {m: delta_by_method[m] for m in ("B", "C", "D", "E")},
        "red_by_category": {m: dict(reds_by_method_cat[m]) for m in ("B", "C", "D", "E")},
        "ema_state": ema_state,
    }


def main():
    results = []
    for i, persona in enumerate(PERSONAS):
        print(f"[{i+1}/10] {persona.name} - {persona.job} (월수입 {persona.monthly_income:,})")
        res = simulate_persona(persona, i)
        print(f"  ✓ {res['total_expenses']}건, {res['elapsed_seconds']}s")
        print(f"  signals: B={res['signals']['B']} C={res['signals']['C']} E={res['signals']['E']}")
        results.append(res)

    with OUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    # Summary
    print()
    print("=" * 80)
    print("전체 집계 (10인 합산)")
    print("=" * 80)
    agg = {m: Counter() for m in ("B", "C", "D", "E")}
    tot_delta = defaultdict(int)
    for r in results:
        for m in ("B", "C", "D", "E"):
            for s, c in r["signals"][m].items():
                agg[m][s] += c
            tot_delta[m] += r["cumulative_delta"][m]
    for m in ("B", "C", "D", "E"):
        print(f"{m}: {dict(agg[m])}  cum_delta={tot_delta[m]}")
    print(f">>> saved: {OUT_PATH}")


if __name__ == "__main__":
    main()
