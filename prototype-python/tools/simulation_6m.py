"""6개월(180일) 대학생 가상 소비 시뮬레이션 + 5방식 전체 비교 분석.

절차:
  1. 현실적 소비 데이터 180일치 생성 (식비/쇼핑/여가/고정)
  2. 각 지출마다 /judge로 B·C·D·E 판정 (A는 이례일 때만 샘플링)
  3. 일별·월별·전체 집계
  4. 방식간 일치율, 이례 탐지 횟수, 스탯 누적, EMA 추이 등 분석
  5. JSON으로 결과 저장 → 이후 마크다운 리포트로 전환

Notes:
  - Gemini 무료 15RPM 제한 → A는 E-이례일 때만 호출 + 4.5초 sleep
  - state_store는 E에 전용. 시뮬레이션 시작 전 초기화.
"""
from __future__ import annotations

import json
import random
import time
import urllib.request
from collections import Counter, defaultdict
from datetime import date, timedelta
from pathlib import Path

BASE = "http://127.0.0.1:8000"
OUT_PATH = Path(__file__).parent / "simulation_6m_result.json"

random.seed(42)

STUDENT = {
    "job": "대학생",
    "monthly_income": 500000,
    "total_assets": 3000000,
    "category_avg": {"식비": 200000, "쇼핑뷰티": 100000, "문화여가": 50000, "생활고정": 400000},
}

# ─────────────────── 가상 소비 생성기 ───────────────────

MEALS_MORNING = [
    ("편의점 삼각김밥", 2500), ("편의점 샌드위치", 3500), ("집밥", 0),
    ("카페 아침 세트", 6000),
]
MEALS_LUNCH = [
    ("학식", 5000), ("김밥천국 점심", 9000), ("편의점 도시락", 5000),
    ("학교 앞 분식", 7000), ("동네 한식집", 9500), ("도시락 지참", 0),
]
MEALS_DINNER = [
    ("집밥 저녁", 0), ("편의점 도시락 저녁", 5500), ("프랜차이즈 저녁", 9000),
    ("동네 식당 저녁", 10000),
]
DINNER_OUT_RARE = [  # 가끔 외식
    ("친구들과 저녁 외식", 22000), ("학교 회식 치킨", 18000),
    ("삼겹살 회식", 35000), ("파스타집", 16000),
]
CAFE = [
    ("스타벅스 아메리카노", 4500), ("카페 라떼", 5500),
    ("동네 카페 디저트", 8000),
]
SHOPPING = [
    ("편의점 생필품", 4000), ("양말 구매", 5000), ("기초 화장품", 25000),
    ("계절옷 티셔츠", 18000), ("세일 바지", 35000), ("신발", 60000),
]
SHOPPING_BIG = [
    ("세일 코트", 85000), ("운동화 교체", 72000), ("명품 충동", 250000),
]
LEISURE = [
    ("영화 티켓", 13000), ("서점 신간", 18000), ("넷플릭스", 17000),
    ("헬스장 월권", 50000), ("도서관 대출", 0), ("전시회", 15000),
    ("방탈출", 22000),
]
LEISURE_BIG = [
    ("콘서트 티켓", 80000), ("뮤지컬", 95000), ("주말 소풍", 40000),
]
FIXED_MONTHLY = [  # 매월 고정
    ("월세", 500000, 1),       # 매달 1일
    ("통신비", 70000, 3),       # 매달 3일
    ("공과금", 80000, 5),       # 매달 5일
    ("넷플릭스 구독", 17000, 10),
]

SPECIAL_EVENTS = [  # 6개월 간 임의 삽입
    ("친구 결혼식 축의금", 100000, "문화여가", "점심", "친한 친구 결혼식"),
    ("응급실 택시비", 40000, "생활고정", "새벽", "어머니 응급실 이송"),
    ("자격증 교재", 45000, "문화여가", "점심", "자격증 준비"),
    ("팀플 회식비", 45000, "식비", "저녁", "팀플 종강 회식"),
    ("가족 생일 외식", 80000, "식비", "저녁", "엄마 생신"),
    ("시험기간 배달 폭주", 28000, "식비", "새벽", None),
    ("개강 책 구매", 120000, "문화여가", "점심", "전공 교재"),
    ("새벽 충동 홈쇼핑", 150000, "쇼핑뷰티", "새벽", None),
]


def _pick(lst, weight=1.0):
    return random.choice(lst)


def generate_6month_expenses(start: date, days: int = 180) -> list[dict]:
    """현실적 대학생 소비 180일치."""
    expenses = []
    # SPECIAL 이벤트를 임의 날짜에 삽입
    special_days = sorted(random.sample(range(15, days - 5), len(SPECIAL_EVENTS)))
    specials = {d: e for d, e in zip(special_days, SPECIAL_EVENTS)}

    for d in range(days):
        cur = start + timedelta(days=d)
        day_expenses: list[dict] = []

        # Special 이벤트
        if d in specials:
            desc, amt, cat, tod, reason = specials[d]
            day_expenses.append({
                "date": cur.isoformat(), "description": desc, "amount": amt,
                "category": cat, "time_of_day": tod, "reason": reason,
            })

        # 식비: 아침(50%), 점심(100%), 저녁(85%)
        if random.random() < 0.5:
            desc, amt = _pick(MEALS_MORNING)
            if amt > 0:
                day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "식비", "time_of_day": "아침", "reason": None})
        desc, amt = _pick(MEALS_LUNCH)
        if amt > 0:
            day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "식비", "time_of_day": "점심", "reason": None})
        if random.random() < 0.85:
            desc, amt = _pick(MEALS_DINNER)
            if random.random() < 0.15:  # 15% 확률로 외식
                desc, amt = _pick(DINNER_OUT_RARE)
            if amt > 0:
                day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "식비", "time_of_day": "저녁", "reason": None})

        # 카페: 30%
        if random.random() < 0.30:
            desc, amt = _pick(CAFE)
            day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "식비", "time_of_day": "아침", "reason": None})

        # 쇼핑: 10% 일반, 3% 큰
        if random.random() < 0.03:
            desc, amt = _pick(SHOPPING_BIG)
            day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "쇼핑뷰티", "time_of_day": "저녁", "reason": None})
        elif random.random() < 0.10:
            desc, amt = _pick(SHOPPING)
            day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "쇼핑뷰티", "time_of_day": random.choice(["점심", "저녁"]), "reason": None})

        # 여가: 15% 일반, 2% 큰
        if random.random() < 0.02:
            desc, amt = _pick(LEISURE_BIG)
            day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "문화여가", "time_of_day": "저녁", "reason": None})
        elif random.random() < 0.15:
            desc, amt = _pick(LEISURE)
            if amt > 0:
                day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "문화여가", "time_of_day": random.choice(["점심", "저녁"]), "reason": None})

        # 고정비: 매달 지정 일자
        for desc, amt, fixed_day in FIXED_MONTHLY:
            if cur.day == fixed_day:
                day_expenses.append({"date": cur.isoformat(), "description": desc, "amount": amt, "category": "생활고정", "time_of_day": "아침", "reason": None})

        expenses.extend(day_expenses)

    return expenses


# ─────────────────── API 유틸 ───────────────────

def post(path: str, body: dict) -> dict:
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def delete(path: str) -> None:
    req = urllib.request.Request(BASE + path, method="DELETE")
    with urllib.request.urlopen(req) as r:
        r.read()


def judge_single(approach: str, exp: dict) -> dict:
    body = {
        "approach": approach,
        "expense": {k: exp[k] for k in ("description", "amount", "category", "time_of_day")},
        "user_context": STUDENT,
        "reason": exp.get("reason"),
    }
    return post("/judge", body)


# ─────────────────── 시뮬레이션 ───────────────────

def main() -> None:
    print(">>> 상태 초기화...")
    delete("/adaptive/state")
    delete("/history")
    try:
        delete("/anomaly/state")
    except Exception:
        pass

    start = date(2025, 10, 13)  # 6개월 전
    days = 180
    print(f">>> 데이터 생성: {start} ~ {start + timedelta(days=days - 1)} ({days}일)")
    expenses = generate_6month_expenses(start, days)
    print(f">>> 총 {len(expenses)}건")

    print(">>> 판정 시작... (B·C·D·E 항상, A는 E-이례일 때만 샘플링)")
    records = []
    a_sample_count = 0
    a_sample_limit = 40  # 최대 40회까지만 Gemini 호출
    start_t = time.time()

    for i, exp in enumerate(expenses):
        if i % 50 == 0 and i > 0:
            elapsed = time.time() - start_t
            print(f"  {i}/{len(expenses)} ... {elapsed:.0f}s 경과, A샘플 {a_sample_count}건")

        result = {"expense": exp, "judgments": {}}

        # B·C·D·E 순차 (각각 결정론적, 빠름)
        for m in ("B", "C", "D", "E"):
            try:
                r = judge_single(m, exp)
                result["judgments"][m] = {"signal": r["signal"], "stat_delta": r["stat_delta"], "reasoning": r["reasoning"][:100]}
            except Exception as e:
                result["judgments"][m] = {"error": str(e)[:80]}

        # A는 E가 이례(red)이고 샘플 한도 이내일 때만
        e_signal = result["judgments"].get("E", {}).get("signal")
        if e_signal == "red" and a_sample_count < a_sample_limit:
            try:
                r_a = judge_single("A", exp)
                result["judgments"]["A"] = {"signal": r_a["signal"], "stat_delta": r_a["stat_delta"], "reasoning": r_a["reasoning"][:150]}
                a_sample_count += 1
                time.sleep(4.5)  # 15 RPM 레이트 제한
            except Exception as e:
                result["judgments"]["A"] = {"error": str(e)[:80]}

        records.append(result)

    elapsed = time.time() - start_t
    print(f">>> 판정 완료: {elapsed:.0f}s, A샘플 {a_sample_count}건")

    # ─── 집계 ───
    print(">>> 집계...")

    by_method_signal = defaultdict(Counter)
    by_method_stat = defaultdict(int)
    by_month_signal = defaultdict(lambda: defaultdict(Counter))
    ex_seen = defaultdict(lambda: Counter())  # month → method → signal

    for rec in records:
        month = rec["expense"]["date"][:7]  # "2025-10"
        for m, j in rec["judgments"].items():
            if "error" in j:
                continue
            s = j["signal"]
            by_method_signal[m][s] += 1
            by_method_stat[m] += j["stat_delta"]
            by_month_signal[month][m][s] += 1

    # 방식 간 일치율
    pair_agree = defaultdict(lambda: [0, 0])  # pair → [agree, total]
    methods = ["B", "C", "D", "E"]
    for rec in records:
        sigs = {m: rec["judgments"].get(m, {}).get("signal") for m in methods}
        sigs = {m: s for m, s in sigs.items() if s}
        for i, m1 in enumerate(methods):
            for m2 in methods[i + 1:]:
                if m1 in sigs and m2 in sigs:
                    pair_agree[f"{m1}-{m2}"][1] += 1
                    if sigs[m1] == sigs[m2]:
                        pair_agree[f"{m1}-{m2}"][0] += 1

    # 이례(red) 탐지 건수
    red_counts = {m: by_method_signal[m].get("red", 0) for m in methods}

    # E의 EMA 최종 상태
    ema_state = post("/adaptive/state" if False else "/adaptive/state", {}) if False else None
    try:
        with urllib.request.urlopen(BASE + "/adaptive/state") as r:
            ema_state = json.load(r)
    except Exception:
        ema_state = None

    # A 샘플 비교 (A도 있는 케이스만 골라서)
    a_samples = [r for r in records if "A" in r["judgments"] and "signal" in r["judgments"]["A"]]
    a_vs_e = Counter()  # ('A', 'E') 시그널 쌍
    for r in a_samples:
        a_sig = r["judgments"]["A"]["signal"]
        e_sig = r["judgments"]["E"]["signal"]
        a_vs_e[(a_sig, e_sig)] += 1

    summary = {
        "total_expenses": len(records),
        "days": days,
        "start_date": str(start),
        "end_date": str(start + timedelta(days=days - 1)),
        "elapsed_seconds": elapsed,
        "a_samples": a_sample_count,
        "by_method_signal": {m: dict(by_method_signal[m]) for m in methods + ["A"]},
        "by_method_stat_cumulative": dict(by_method_stat),
        "by_month_signal": {mo: {m: dict(c) for m, c in mc.items()} for mo, mc in by_month_signal.items()},
        "pair_agreement_rate": {k: {"agree": v[0], "total": v[1], "rate": v[0] / v[1] if v[1] else 0} for k, v in pair_agree.items()},
        "red_counts": red_counts,
        "ema_final_state": ema_state,
        "a_vs_e_signals": {f"A={a}/E={e}": cnt for (a, e), cnt in a_vs_e.items()},
    }

    with OUT_PATH.open("w", encoding="utf-8") as f:
        json.dump({"summary": summary, "records": records}, f, ensure_ascii=False, indent=2)
    print(f">>> 결과 저장: {OUT_PATH}")
    print("=" * 60)
    print(json.dumps(summary, ensure_ascii=False, indent=2, default=str))


if __name__ == "__main__":
    main()
