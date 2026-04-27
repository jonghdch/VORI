"""10인 페르소나 × 6개월 심층 시뮬레이션.

기존 simulation_multi.py 확장:
  - 지출건별 (id, date, cat, amount, signal by method) 기록
  - 카테고리별 총 지출
  - 금액대별 신호등 분포
  - 페르소나별 절약액 계산 (개인 평균 대비 덜 쓴 누계)
  - 현실성 지표 (월 평균 지출 vs 예산 비율)
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
from simulation_multi import generate_expenses  # 재사용

BASE_HOST = "127.0.0.1"
BASE_PORT = 8000
OUT_PATH = Path(__file__).parent / "simulation_deep_result.json"
SEED_BASE = 1000

_conn: http.client.HTTPConnection | None = None


def _get_conn():
    global _conn
    if _conn is None:
        _conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=60)
    return _conn


def _request(method: str, path: str, body=None):
    global _conn
    for attempt in range(3):
        try:
            conn = _get_conn()
            headers = {"Content-Type": "application/json"} if body is not None else {}
            data = json.dumps(body).encode() if body is not None else None
            conn.request(method, path, body=data, headers=headers)
            resp = conn.getresponse()
            text = resp.read().decode("utf-8", errors="ignore")
            if resp.status >= 400:
                raise RuntimeError(f"{resp.status}: {text[:200]}")
            return json.loads(text) if text else {}
        except (http.client.HTTPException, OSError, ConnectionError):
            try:
                if _conn:
                    _conn.close()
            except Exception:
                pass
            _conn = None
            if attempt == 2:
                raise
            time.sleep(0.3)
    return {}


def judge(method: str, exp: dict, context: dict) -> dict:
    return _request("POST", "/judge", {
        "approach": method,
        "expense": {k: exp[k] for k in ("description", "amount", "category", "time_of_day")},
        "user_context": context,
        "reason": exp.get("reason"),
    })


def categorize_amount(amt: int, persona: Persona) -> str:
    """금액을 페르소나 식비 평균 대비 몇 배인지로 bucket."""
    base = persona.category_avg["식비"] / 30  # 일 평균
    r = amt / max(base, 1)
    if r <= 0.5:
        return "초소액(≤0.5x)"
    if r <= 1.5:
        return "소액(0.5~1.5x)"
    if r <= 3:
        return "중액(1.5~3x)"
    if r <= 8:
        return "대액(3~8x)"
    return "극대액(>8x)"


def simulate_persona_deep(persona: Persona, seed_idx: int) -> dict:
    _request("DELETE", "/adaptive/state")
    _request("DELETE", "/history")

    expenses = generate_expenses(persona, days=180, seed=SEED_BASE + seed_idx)

    # 누적 지표
    signals_by_method = defaultdict(Counter)
    delta_by_method = defaultdict(int)
    cat_spent = defaultdict(int)
    cat_count = defaultdict(int)
    signal_by_bucket = defaultdict(lambda: defaultdict(Counter))  # method -> bucket -> sig counter
    signal_by_cat = defaultdict(lambda: defaultdict(Counter))      # method -> cat -> sig counter
    records = []  # 모든 지출 상세

    # E의 본인 평균 기준 절약 추적 (판정 시점에 EMA μ 기록됨. 사후엔 불가)
    # 매 지출 직전 E의 mu를 조회하면 느림. 대신 expense 누적 vs 최종 EMA μ 사용해서 근사.

    for exp in expenses:
        cat = exp["category"]
        amt = exp["amount"]
        cat_spent[cat] += amt
        cat_count[cat] += 1
        bucket = categorize_amount(amt, persona)

        judgments = {}
        for m in ("B", "C", "D", "E"):
            try:
                r = judge(m, exp, persona.context)
                judgments[m] = {"signal": r["signal"], "delta": r["stat_delta"]}
                signals_by_method[m][r["signal"]] += 1
                delta_by_method[m] += r["stat_delta"]
                signal_by_bucket[m][bucket][r["signal"]] += 1
                signal_by_cat[m][cat][r["signal"]] += 1
            except Exception as e:
                judgments[m] = {"error": str(e)[:80]}

        records.append({
            "date": exp["date"],
            "description": exp["description"],
            "amount": amt,
            "category": cat,
            "time_of_day": exp["time_of_day"],
            "reason": exp.get("reason"),
            "judgments": judgments,
        })

    # E 최종 상태
    ema_state = _request("GET", "/adaptive/state")

    # 절약액 계산 (E 기준)
    # 각 카테고리에서 "E 평균 갱신 최종 μ"를 이용, total_spent - μ × count
    # 음수면 절약(아낀 금액), 양수면 초과
    savings_estimate = {}
    for cat, st in ema_state.get("categories", {}).items():
        mu = st.get("mu", 0)
        count = cat_count.get(cat, 0)
        spent = cat_spent.get(cat, 0)
        expected = mu * count
        # 절약 = expected(본인 평균 × 건수) - actual spent. 양수면 절약
        savings_estimate[cat] = {
            "spent": spent,
            "expected": round(expected),
            "saved": round(expected - spent),  # 양수: 아낌
            "count": count,
        }

    return {
        "name": persona.name,
        "job": persona.job,
        "income": persona.monthly_income,
        "assets": persona.total_assets,
        "category_avg": persona.category_avg,
        "scales": {
            "meal_scale": persona.meal_scale,
            "dine_out_prob": persona.dine_out_prob,
            "cafe_prob": persona.cafe_prob,
        },
        "total_expenses": len(records),
        "total_spent_per_category": dict(cat_spent),
        "count_per_category": dict(cat_count),
        "signals": {m: dict(signals_by_method[m]) for m in ("B", "C", "D", "E")},
        "cumulative_delta": {m: delta_by_method[m] for m in ("B", "C", "D", "E")},
        "signal_by_bucket": {
            m: {b: dict(c) for b, c in bm.items()}
            for m, bm in signal_by_bucket.items()
        },
        "signal_by_category": {
            m: {c: dict(s) for c, s in cm.items()}
            for m, cm in signal_by_cat.items()
        },
        "ema_state": ema_state,
        "savings_estimate": savings_estimate,
        "records": records,
    }


def main():
    results = []
    t_total = time.time()
    for i, persona in enumerate(PERSONAS):
        print(f"[{i+1}/10] {persona.name}")
        t = time.time()
        res = simulate_persona_deep(persona, i)
        print(f"  ✓ {res['total_expenses']}건, {time.time()-t:.1f}s")
        results.append(res)

    with OUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f">>> total {time.time()-t_total:.0f}s, saved to {OUT_PATH}")


if __name__ == "__main__":
    main()
