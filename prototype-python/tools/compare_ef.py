"""E vs F 비교 — 10 페르소나 동일 데이터로 두 방식 성능 대조."""
from __future__ import annotations

import http.client
import json
import time
from collections import Counter, defaultdict
from pathlib import Path
import statistics

from personas import PERSONAS, Persona
from simulation_multi import generate_expenses

BASE_HOST = "127.0.0.1"
BASE_PORT = 8000
OUT_PATH = Path(__file__).parent / "compare_ef_result.json"
SEED_BASE = 1000

_conn = None


def _conn_get():
    global _conn
    if _conn is None:
        _conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=60)
    return _conn


def _req(method, path, body=None):
    global _conn
    for i in range(3):
        try:
            c = _conn_get()
            headers = {"Content-Type": "application/json"} if body else {}
            data = json.dumps(body).encode() if body else None
            c.request(method, path, body=data, headers=headers)
            r = c.getresponse()
            t = r.read().decode("utf-8", errors="ignore")
            if r.status >= 400:
                raise RuntimeError(f"{r.status}: {t[:150]}")
            return json.loads(t) if t else {}
        except (http.client.HTTPException, OSError):
            try:
                if _conn:
                    _conn.close()
            except Exception:
                pass
            _conn = None
            if i == 2:
                raise
            time.sleep(0.2)


def judge(m, exp, ctx):
    return _req("POST", "/judge", {
        "approach": m,
        "expense": {k: exp[k] for k in ("description", "amount", "category", "time_of_day")},
        "user_context": ctx,
        "reason": exp.get("reason"),
    })


def simulate(persona: Persona, idx: int) -> dict:
    _req("DELETE", "/adaptive/state")
    _req("DELETE", "/history")
    expenses = generate_expenses(persona, days=180, seed=SEED_BASE + idx)

    sig = {"E": Counter(), "F": Counter()}
    delta = {"E": 0, "F": 0}
    red_cat = {"E": defaultdict(int), "F": defaultdict(int)}
    bucket_counts = {"E": Counter(), "F": Counter()}  # F는 bucket info
    records = []

    for exp in expenses:
        row = {"desc": exp["description"], "amount": exp["amount"], "category": exp["category"], "time": exp["time_of_day"]}
        for m in ("E", "F"):
            try:
                r = judge(m, exp, persona.context)
                sig[m][r["signal"]] += 1
                delta[m] += r["stat_delta"]
                if r["signal"] == "red":
                    red_cat[m][exp["category"]] += 1
                row[m] = {"signal": r["signal"], "delta": r["stat_delta"], "reasoning": r["reasoning"][:80]}
            except Exception as e:
                row[m] = {"error": str(e)[:50]}
        records.append(row)

    return {
        "name": persona.name,
        "job": persona.job,
        "income": persona.monthly_income,
        "total": len(expenses),
        "signals": {m: dict(sig[m]) for m in ("E", "F")},
        "delta": delta,
        "red_by_cat": {m: dict(red_cat[m]) for m in ("E", "F")},
        "records_sample": records[::50][:20],  # 일부만 저장
    }


def main():
    results = []
    for i, p in enumerate(PERSONAS):
        print(f"[{i+1}/10] {p.name}")
        t = time.time()
        res = simulate(p, i)
        print(f"  ✓ {res['total']}건, {time.time()-t:.1f}s")
        print(f"  E: {res['signals']['E']}  delta {res['delta']['E']:+d}")
        print(f"  F: {res['signals']['F']}  delta {res['delta']['F']:+d}")
        results.append(res)

    with OUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    # Aggregate
    agg = {"E": Counter(), "F": Counter()}
    total_delta = {"E": 0, "F": 0}
    for r in results:
        for m in ("E", "F"):
            for s, c in r["signals"][m].items():
                agg[m][s] += c
            total_delta[m] += r["delta"][m]

    print()
    print("=" * 70)
    print("전체 집계 (10인)")
    print("=" * 70)
    for m in ("E", "F"):
        tot = sum(agg[m].values())
        red_rate = agg[m].get("red", 0) / tot * 100
        print(f"{m}: {dict(agg[m])}  cum_delta={total_delta[m]}  red_rate={red_rate:.2f}%")

    # CoV 계산
    for m in ("E", "F"):
        rates = [r["signals"][m].get("red", 0) / r["total"] * 100 for r in results]
        mean = statistics.mean(rates)
        stdev = statistics.stdev(rates)
        print(f"{m}: red% mean={mean:.2f}, stdev={stdev:.2f}, CoV={stdev/mean*100 if mean>0 else 0:.1f}%")

    # 페르소나별 비교
    print()
    print(f"{'persona':<20} {'E_red%':>8} {'F_red%':>8} {'변화':>8}")
    for r in results:
        er = r["signals"]["E"].get("red", 0) / r["total"] * 100
        fr = r["signals"]["F"].get("red", 0) / r["total"] * 100
        print(f"{r['name']:<20} {er:>7.1f}% {fr:>7.1f}% {fr-er:>+7.1f}%p")


if __name__ == "__main__":
    main()
