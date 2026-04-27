"""simulation_deep_result.json 심층 분석."""
from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

IN_PATH = Path(__file__).parent / "simulation_deep_result.json"

with IN_PATH.open(encoding="utf-8") as f:
    data = json.load(f)


def fmt(n):
    return f"{int(n):,}"


def pct(n, total):
    return f"{n/total*100:.1f}%" if total > 0 else "-"


print("=" * 100)
print("1. 절약액 분석 (E 방식 최종 EMA μ 기준)")
print("=" * 100)
print(f"{'페르소나':<20} {'총지출':>12} {'카테고리 절약':>30}")
print("-" * 100)

total_saved_all = 0
for r in data:
    cat_totals = r["total_spent_per_category"]
    savings = r["savings_estimate"]
    total_spent = sum(cat_totals.values())
    total_saved = sum(v.get("saved", 0) for v in savings.values())
    # 생활고정은 E가 위임하므로 savings에 없음
    saving_details = []
    for cat in ("식비", "쇼핑뷰티", "문화여가"):
        if cat in savings:
            s = savings[cat]["saved"]
            saving_details.append(f"{cat}{'+' if s>=0 else ''}{fmt(s)}")
    total_saved_all += total_saved
    print(f"{r['name']:<20} {fmt(total_spent):>12}  | {' / '.join(saving_details)}")

print(f"\n전체 10인 E 기준 절약 추정 총액: {fmt(total_saved_all)}원")
print("※ 'E가 본인 평균 μ_c×count_c 로 기대했는데 실제는 얼마나 다른가'의 수치화. 음수면 평균보다 더 썼다는 뜻.")

print()
print("=" * 100)
print("2. 금액대별 신호등 분포 (10인 종합)")
print("=" * 100)

agg_bucket = defaultdict(lambda: defaultdict(Counter))
for r in data:
    for m, bm in r["signal_by_bucket"].items():
        for b, sc in bm.items():
            for s, c in sc.items():
                agg_bucket[m][b][s] += c

buckets_order = ["초소액(≤0.5x)", "소액(0.5~1.5x)", "중액(1.5~3x)", "대액(3~8x)", "극대액(>8x)"]
for m in ("B", "C", "D", "E"):
    print(f"\n[{m}]")
    print(f"  {'구간':<20} {'🟢':>8} {'⚪':>8} {'🔴':>8} {'총':>8}")
    for b in buckets_order:
        c = agg_bucket[m].get(b, Counter())
        g, gr, r_ = c.get("green", 0), c.get("gray", 0), c.get("red", 0)
        tot = g + gr + r_
        print(f"  {b:<22} {g:>8} {gr:>8} {r_:>8} {tot:>8}")

print()
print("=" * 100)
print("3. 카테고리별 신호등 분포 (10인 종합)")
print("=" * 100)

agg_cat = defaultdict(lambda: defaultdict(Counter))
for r in data:
    for m, cm in r["signal_by_category"].items():
        for c, sc in cm.items():
            for s, n in sc.items():
                agg_cat[m][c][s] += n

for m in ("B", "C", "D", "E"):
    print(f"\n[{m}]")
    print(f"  {'카테고리':<12} {'🟢':>8} {'⚪':>8} {'🔴':>8} {'🔴%':>6}")
    for cat in ("식비", "쇼핑뷰티", "문화여가", "생활고정"):
        c = agg_cat[m].get(cat, Counter())
        g, gr, r_ = c.get("green", 0), c.get("gray", 0), c.get("red", 0)
        tot = g + gr + r_
        print(f"  {cat:<12} {g:>8} {gr:>8} {r_:>8} {pct(r_, tot):>6}")

print()
print("=" * 100)
print("4. 데이터 현실성 검증 (월 평균 지출 vs 예산)")
print("=" * 100)
print(f"{'페르소나':<20} {'월 예산':>15} {'월 평균실지출':>15} {'비율':>8}")
print("-" * 80)
for r in data:
    cat_avg = r["category_avg"]
    cat_spent = r["total_spent_per_category"]
    total_budget = sum(cat_avg.values())
    total_actual = sum(cat_spent.values()) / 6.0  # 월 평균
    ratio = total_actual / total_budget * 100
    print(f"{r['name']:<20} {fmt(total_budget):>15} {fmt(total_actual):>15} {ratio:>7.1f}%")

print()
print("=" * 100)
print("5. 방식별 이례 탐지 중 겹침 분석")
print("=" * 100)

# 각 expense에 대해 판정 시그널 수집 → 4방식 모두 red 한 경우 / E만 red 한 경우 등
all_reds = {"B": set(), "C": set(), "D": set(), "E": set()}
for r in data:
    for i, rec in enumerate(r["records"]):
        key = f"{r['name']}_{i}"
        for m in ("B", "C", "D", "E"):
            j = rec["judgments"].get(m)
            if j and j.get("signal") == "red":
                all_reds[m].add(key)

print(f"전체 red 건수: B={len(all_reds['B'])}, C={len(all_reds['C'])}, D={len(all_reds['D'])}, E={len(all_reds['E'])}")
print()
print("겹침 세부 (어떤 방식 조합이 동시에 red 했는가):")
only_e = all_reds["E"] - all_reds["B"] - all_reds["C"]
only_b = all_reds["B"] - all_reds["E"] - all_reds["C"]
only_c = all_reds["C"] - all_reds["E"] - all_reds["B"]
e_and_b = all_reds["E"] & all_reds["B"] - all_reds["C"]
all_three = all_reds["E"] & all_reds["B"] & all_reds["C"]
print(f"  E만 red (B·C 무관): {len(only_e)}")
print(f"  B만 red (E·C 무관): {len(only_b)}")
print(f"  C만 red: {len(only_c)}")
print(f"  E+B 동시 red (C는 green/gray): {len(e_and_b)}")
print(f"  E+B+C 모두 red: {len(all_three)}")

print()
print("=" * 100)
print("6. 'E만 red' 대표 사례 20건 (B·C는 관대하지만 E가 잡은 것)")
print("=" * 100)
shown = 0
for r in data:
    if shown >= 20:
        break
    for i, rec in enumerate(r["records"]):
        if shown >= 20:
            break
        key = f"{r['name']}_{i}"
        if key in only_e:
            j = rec["judgments"]
            print(f"  [{r['name']}] {rec['description']} {fmt(rec['amount'])}원 ({rec['category']}/{rec['time_of_day']}) "
                  f"| B={j['B']['signal']} C={j['C']['signal']} E={j['E']['signal']}")
            shown += 1

print()
print("=" * 100)
print("7. 'B만 red' 대표 사례 20건 (E는 관대하지만 B가 잡은 것)")
print("=" * 100)
shown = 0
for r in data:
    if shown >= 20:
        break
    for i, rec in enumerate(r["records"]):
        if shown >= 20:
            break
        key = f"{r['name']}_{i}"
        if key in only_b:
            j = rec["judgments"]
            print(f"  [{r['name']}] {rec['description']} {fmt(rec['amount'])}원 ({rec['category']}/{rec['time_of_day']}) "
                  f"| B={j['B']['signal']} C={j['C']['signal']} E={j['E']['signal']}")
            shown += 1
