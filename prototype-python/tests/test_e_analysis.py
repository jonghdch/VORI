"""방식 E 상세 검증·분석 스크립트.

검증 항목:
  1. 시드 수집 → 평균·편차 생성 품질
  2. 이례 탐지 정확도
  3. EMA 적응 속도 (평균이 얼마나 빨리 따라오나)
  4. 카테고리별 독립 작동
  5. 생활고정 처리
  6. 사유 반영 재판정 (Gemini 연동)
  7. 경계 케이스 (z 임계치 근처)
"""
from __future__ import annotations

import json
import time
import urllib.request

BASE = "http://127.0.0.1:8000"

STUDENT = {
    "job": "대학생",
    "monthly_income": 500000,
    "total_assets": 3000000,
    "category_avg": {"식비": 200000, "쇼핑뷰티": 100000, "문화여가": 50000, "생활고정": 400000},
}


def post(path: str, body: dict) -> dict:
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def get(path: str) -> dict:
    with urllib.request.urlopen(BASE + path) as r:
        return json.load(r)


def delete(path: str) -> None:
    req = urllib.request.Request(BASE + path, method="DELETE")
    with urllib.request.urlopen(req) as r:
        r.read()


def judge(desc: str, amount: int, category: str, time_of_day: str = "점심", reason=None) -> dict:
    body = {
        "expense": {"description": desc, "amount": amount, "category": category, "time_of_day": time_of_day},
        "user_context": STUDENT,
        "reason": reason,
    }
    res = post("/judge-all", body)
    return next(r for r in res if r["approach"] == "E")


def state_cat(category: str) -> dict:
    s = get("/adaptive/state")
    return s["categories"].get(category, {})


def sep(title: str) -> None:
    print()
    print("=" * 70)
    print(f"  {title}")
    print("=" * 70)


# ─────────────────────────────────────────────────────────────
# 시험 시작
# ─────────────────────────────────────────────────────────────

delete("/adaptive/state")
delete("/history")
print(">>> 상태 초기화 완료")

# ─────────────────────────────────────────────────────────────
sep("1. 시드 수집 (식비 3회, 현실적 다양성)")
# ─────────────────────────────────────────────────────────────
seeds = [("학식", 5000), ("편의점 도시락", 6500), ("저녁 외식 한끼", 12000)]
for desc, amt in seeds:
    r = judge(desc, amt, "식비", "점심")
    st = state_cat("식비")
    print(f"  {desc:<16} {amt:>6,}원  →  {r['signal']:5s} δ{r['stat_delta']:+d} | {r['reasoning'][:55]}")
    print(f"    state: μ={st['mu']:,.0f} σ={st['sigma']:,.0f} count={st['count']}")

# ─────────────────────────────────────────────────────────────
sep("2. 본판: 경계 테스트 (z 임계치 근처)")
# ─────────────────────────────────────────────────────────────
st = state_cat("식비")
mu, sigma = st["mu"], max(st["sigma"], st["mu"] * 0.3)
print(f"현재 평균 {mu:,.0f}원 편차 {sigma:,.0f}원")
print(f"  z=0 → {mu:,.0f}원  / z=1 → {mu+sigma:,.0f}원 / z=2 → {mu+2*sigma:,.0f}원")
print()
test_amounts = [
    ("절약 - 학식",           5500),
    ("평균근처 - 편의점",     7000),
    ("약간초과 - 분식",       10000),
    ("뚜렷초과 - 점심외식",   16000),
    ("이례 - 저녁회식",       35000),
    ("극단 - 호텔뷔페",       150000),
]
for desc, amt in test_amounts:
    z_est = (amt - mu) / sigma
    r = judge(desc, amt, "식비", "점심")
    print(f"  {desc:<20} {amt:>7,}원  z≈{z_est:+.2f}  →  {r['signal']:5s} δ{r['stat_delta']:+d}")
    st2 = state_cat("식비")
    print(f"       [μ={st2['mu']:,.0f} σ={st2['sigma']:,.0f} count={st2['count']}]")

# ─────────────────────────────────────────────────────────────
sep("3. EMA 적응 관찰 — 사용자가 점진적으로 지출 패턴 변경")
# ─────────────────────────────────────────────────────────────
delete("/adaptive/state")
print("상태 초기화")

# 재시드: 가벼운 식사 위주 (현실적 편차)
for amt in [5000, 5500, 4500]:
    judge("학식", amt, "식비", "점심")
st = state_cat("식비")
print(f"재시드 완료: μ={st['mu']:,.0f} σ={st['sigma']:,.0f}")
print()
print("이후 15일간 '매일 15000원' 외식으로 지출 패턴 변화:")
print(f"  {'일':<4} {'지출':<8} {'signal':<8} {'δ':<5} {'μ갱신':<12} {'σ갱신':<10}")
for day in range(1, 16):
    r = judge(f"점심 외식 day{day}", 15000, "식비", "점심")
    st = state_cat("식비")
    print(f"  {day:<4} 15,000원  {r['signal']:<8} {r['stat_delta']:+5d} {st['mu']:>10,.0f}   {st['sigma']:>8,.0f}")

# ─────────────────────────────────────────────────────────────
sep("4. 카테고리 독립성 확인")
# ─────────────────────────────────────────────────────────────
delete("/adaptive/state")
# 식비·쇼핑뷰티 각각 따로 시드
for amt in [5000, 6000, 7000]:
    judge("학식", amt, "식비", "점심")
for amt in [10000, 15000, 20000]:
    judge("생필품", amt, "쇼핑뷰티", "저녁")
print("시드 완료 후 각 카테고리 상태:")
s = get("/adaptive/state")
for c, v in s["categories"].items():
    if v["count"] > 0:
        print(f"  {c:<10} μ={v['mu']:>8,.0f} σ={v['sigma']:>8,.0f} count={v['count']}")

# ─────────────────────────────────────────────────────────────
sep("5. 이례 풀 우선순위 확인")
# ─────────────────────────────────────────────────────────────
delete("/adaptive/state")
delete("/history")
# 식비 시드
for amt in [5000, 6000, 7000]:
    judge("학식", amt, "식비", "점심")
# 여러 이례 발생
anomaly_cases = [
    ("저녁 외식 15000", 15000),
    ("콘서트 티켓", 120000),
    ("명품 지갑", 800000),
    ("친구 생일 파티", 80000),
]
print("이례 사례 여러 개 추가:")
for desc, amt in anomaly_cases:
    r = judge(desc, amt, "식비", "저녁")
    print(f"  {desc:<20} {amt:>8,}  →  {r['signal']} δ{r['stat_delta']}")

pool = get("/anomaly/all")
print(f"\n풀 크기: {len(pool)}건 (우선순위 순)")
for item in pool[:5]:
    e = item["expense"]
    print(f"  #{item['id']} prio={item['priority']:>8.2f}  {e['description']:<20} {e['amount']:>8,}원  z={item['z']:.2f}")

top = get("/anomaly/today")
print(f"\n[오늘의 질문] {top.get('item', {}).get('expense', {}).get('description', 'N/A')}")

# ─────────────────────────────────────────────────────────────
sep("6. 생활고정 처리")
# ─────────────────────────────────────────────────────────────
delete("/adaptive/state")
# 고정비는 월 2회 가정 (월세+공과금 대략)
for amt in [500000, 80000, 70000]:
    r = judge(f"고정비{amt}", amt, "생활고정", "아침")
print("생활고정 시드 후 상태:")
st = state_cat("생활고정")
print(f"  μ={st['mu']:,.0f} σ={st['sigma']:,.0f}")
print()
print("생활고정 판정 (E방식이 고정비 편차 큼을 어떻게 처리?):")
tests = [("월세", 500000), ("넷플릭스", 17000), ("통신비", 70000)]
for desc, amt in tests:
    r = judge(desc, amt, "생활고정", "아침")
    print(f"  {desc:<12} {amt:>8,}원 → {r['signal']:<6} δ{r['stat_delta']:+d} | {r['reasoning'][:70]}")

# ─────────────────────────────────────────────────────────────
sep("7. 사유 기반 재판정 (Gemini 연동)")
# ─────────────────────────────────────────────────────────────
delete("/adaptive/state")
for amt in [5000, 6000, 7000]:
    judge("학식", amt, "식비", "점심")

# 이례 생성 + 사유 즉시 제공
r_no_reason = judge("친구 결혼식 축의금", 100000, "식비", "점심")
print(f"사유 없음: {r_no_reason['signal']} δ{r_no_reason['stat_delta']} | {r_no_reason['reasoning']}")

# 재시드 후 같은 지출에 사유 함께
delete("/adaptive/state")
for amt in [5000, 6000, 7000]:
    judge("학식", amt, "식비", "점심")
r_with_reason = judge("친구 결혼식 축의금", 100000, "식비", "점심", reason="친한 친구 결혼식 축의금")
print(f"사유 있음: {r_with_reason['signal']} δ{r_with_reason['stat_delta']} | {r_with_reason['reasoning']}")

print()
print(">>> 분석 완료. <<<")
