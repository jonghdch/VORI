"""Z_THRESHOLD 비교 — 같은 488건 데이터에 E 방식을 두 번 돌려 차이 측정.

1차: Z=2.0 (기본)
2차: Z=2.5 (완화)

A(Gemini) 호출 없음, E만 호출하여 빠른 비교.
"""
from __future__ import annotations

import json
import urllib.request
from collections import Counter, defaultdict

from simulation_6m import STUDENT, generate_6month_expenses
from datetime import date

BASE = "http://127.0.0.1:8000"


def post(path, body):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def delete(path):
    req = urllib.request.Request(BASE + path, method="DELETE")
    with urllib.request.urlopen(req) as r:
        r.read()


def run_once(expenses, label):
    delete("/adaptive/state")
    delete("/history")
    results = []
    for exp in expenses:
        body = {
            "approach": "E",
            "expense": {k: exp[k] for k in ("description", "amount", "category", "time_of_day")},
            "user_context": STUDENT,
            "reason": exp.get("reason"),
        }
        r = post("/judge", body)
        results.append({"expense": exp, "signal": r["signal"], "delta": r["stat_delta"]})
    sig_count = Counter(r["signal"] for r in results)
    total_delta = sum(r["delta"] for r in results)
    print(f"[{label}] signals: {dict(sig_count)}, cum_delta: {total_delta}")
    return results, sig_count, total_delta


if __name__ == "__main__":
    # Same seed -> same 488 expenses
    import random
    random.seed(42)
    expenses = generate_6month_expenses(date(2025, 10, 13), 180)
    print(f">>> 데이터 {len(expenses)}건 재생성 완료")
    results, signals, delta = run_once(expenses, "현재 Z값")
    out = {
        "count": len(expenses),
        "signals": dict(signals),
        "cumulative_delta": delta,
        "per_category_red": {},
        "red_examples": [],
    }
    # 카테고리별 red 개수
    per_cat = defaultdict(lambda: Counter())
    for r in results:
        per_cat[r["expense"]["category"]][r["signal"]] += 1
    out["per_category"] = {c: dict(v) for c, v in per_cat.items()}
    # 이례 샘플 5건
    reds = [r for r in results if r["signal"] == "red"]
    out["red_examples"] = [
        {"desc": r["expense"]["description"], "amount": r["expense"]["amount"], "category": r["expense"]["category"]}
        for r in reds[:10]
    ]
    out["red_count"] = len(reds)
    print(json.dumps(out, ensure_ascii=False, indent=2))
