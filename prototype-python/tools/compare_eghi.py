"""E vs G vs H vs I 비교."""
from __future__ import annotations

import http.client
import json
import statistics
import time
from collections import Counter, defaultdict
from pathlib import Path

from personas import PERSONAS, Persona
from simulation_multi import generate_expenses

BASE_HOST = "127.0.0.1"
BASE_PORT = 8000
OUT_PATH = Path(__file__).parent / "compare_eghi_result.json"
SEED_BASE = 1000
METHODS = ("E", "G", "H", "I")

_conn = None


def _get_conn():
    global _conn
    if _conn is None:
        _conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=60)
    return _conn


def _req(method, path, body=None):
    global _conn
    for i in range(3):
        try:
            c = _get_conn()
            headers = {"Content-Type": "application/json"} if body else {}
            data = json.dumps(body).encode() if body else None
            c.request(method, path, body=data, headers=headers)
            r = c.getresponse()
            t = r.read().decode("utf-8", errors="ignore")
            if r.status >= 400:
                raise RuntimeError(f"{r.status}: {t[:120]}")
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
        "expense": {
            "description": exp["description"],
            "amount": exp["amount"],
            "category": exp["category"],
            "time_of_day": exp["time_of_day"],
            "date": exp.get("date"),
        },
        "user_context": ctx,
        "reason": exp.get("reason"),
    })


def simulate(persona: Persona, idx: int) -> dict:
    _req("DELETE", "/adaptive/state")
    _req("DELETE", "/history")
    expenses = generate_expenses(persona, days=180, seed=SEED_BASE + idx)

    sig = {m: Counter() for m in METHODS}
    delta = {m: 0 for m in METHODS}
    red_cat = {m: defaultdict(int) for m in METHODS}

    for exp in expenses:
        for m in METHODS:
            try:
                r = judge(m, exp, persona.context)
                s = r["signal"]
                sig[m][s] += 1
                delta[m] += r["stat_delta"]
                if s == "red":
                    red_cat[m][exp["category"]] += 1
            except Exception:
                sig[m]["error"] += 1

    return {
        "name": persona.name,
        "job": persona.job,
        "income": persona.monthly_income,
        "total": len(expenses),
        "signals": {m: dict(sig[m]) for m in METHODS},
        "delta": delta,
        "red_by_cat": {m: dict(red_cat[m]) for m in METHODS},
    }


def main():
    results = []
    t0 = time.time()
    for i, p in enumerate(PERSONAS):
        print(f"[{i+1}/10] {p.name}")
        res = simulate(p, i)
        for m in METHODS:
            tot = res["total"]
            red = res["signals"][m].get("red", 0)
            print(f"  {m}: {res['signals'][m]} Δ={res['delta'][m]:+d} red%={red/tot*100:.1f}")
        results.append(res)

    print(f">>> 총 {time.time()-t0:.0f}s")
    with OUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    agg = {m: Counter() for m in METHODS}
    cum = {m: 0 for m in METHODS}
    for r in results:
        for m in METHODS:
            for s, c in r["signals"][m].items():
                agg[m][s] += c
            cum[m] += r["delta"][m]

    print()
    print("=" * 80)
    print(f"{'방식':<6} {'green':>6} {'gray':>6} {'red':>6} {'red%':>7} {'cum_delta':>12}")
    for m in METHODS:
        tot = sum(agg[m].values())
        red = agg[m].get("red", 0)
        print(f"{m:<6} {agg[m].get('green',0):>6} {agg[m].get('gray',0):>6} {red:>6} {red/tot*100:>6.2f}% {cum[m]:>+12}")

    print()
    print("CoV (낮을수록 일관)")
    for m in METHODS:
        rates = [r["signals"][m].get("red", 0) / r["total"] * 100 for r in results]
        mean = statistics.mean(rates)
        stdev = statistics.stdev(rates) if len(rates) > 1 else 0
        print(f"  {m}: mean={mean:.2f}%, stdev={stdev:.2f}, CoV={stdev/mean*100 if mean>0 else 0:.1f}%")

    print()
    print(f"{'persona':<22} " + " ".join(f"{m:>7}" for m in METHODS))
    for r in results:
        line = f"{r['name']:<22} "
        for m in METHODS:
            rate = r['signals'][m].get('red', 0) / r['total'] * 100
            line += f"{rate:>6.1f}% "
        print(line)


if __name__ == "__main__":
    main()
