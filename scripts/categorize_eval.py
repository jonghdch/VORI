"""지출 품목 이름 자동 분류 정확도를 잰다 (로컬 서버 대상).

사용:
  python scripts/categorize_eval.py                      # 기본: scripts/categorize_cases.csv, 관리자 시험 계정
  python scripts/categorize_eval.py --out result.csv     # 품목별 결과를 CSV 로도 남긴다

- clear: 정답이 분명한 품목. 예측이 expected(| 로 여러 개 허용) 안에 들면 정답.
- ambiguous: 이름만으로는 정할 수 없는 품목(편의점·다이소·치킨 등). 정확도에 넣지 않고,
  지금 분류기가 무엇을 얼마나 자신 있게 고르는지만 본다 — 이런 건 사용자에게 물어야 한다.
- 품목마다 Gemini 임베딩 1회를 쓴다.
"""
import argparse
import csv
import sys

import requests

sys.stdout.reconfigure(encoding="utf-8")

ap = argparse.ArgumentParser()
ap.add_argument("--base", default="http://localhost:8080")
ap.add_argument("--cases", default="scripts/categorize_cases.csv")
ap.add_argument("--email", default="admin@vori.com")
ap.add_argument("--password", default="1234")
ap.add_argument("--out")
args = ap.parse_args()

s = requests.Session()
r = s.post(f"{args.base}/api/auth/login", json={"email": args.email, "password": args.password})
r.raise_for_status()

rows = []
with open(args.cases, encoding="utf-8") as f:
    for c in csv.DictReader(f):
        res = s.post(f"{args.base}/api/categories/categorize", json={"name": c["item"]})
        res.raise_for_status()
        body = res.json()
        pred = body.get("leafName") or "(없음)"
        score = body.get("score") or 0.0
        # source 가 없는 예전 서버면 점수 1.0 을 규칙으로 본다
        source = body.get("source") or ("RULE" if score >= 0.999 else "EMBEDDING")
        ok = pred in c["expected"].split("|")
        ask = body.get("askType") or ""
        chips = "/".join(ch["label"] for ch in body.get("candidates") or [])
        rows.append({**c, "predicted": pred, "score": f"{score:.3f}", "source": source, "ok": ok,
                     "ask": ask, "chips": chips})

clear = [r for r in rows if r["kind"] == "clear"]
amb = [r for r in rows if r["kind"] == "ambiguous"]
hit = sum(r["ok"] for r in clear)
if clear:
    print(f"분명한 품목 정확도: {hit}/{len(clear)} = {hit / len(clear):.0%}")
else:
    print("분명한 품목 없음 — 정확도는 건너뛴다")
# 어디서 정했는지 나눠 본다. 내 기록(HISTORY)은 로그인한 계정의 지출에 따라 달라지므로 따로 센다.
for src, label in (("HISTORY", "내 기록으로 정함"), ("RULE", "규칙으로 정함"),
                   ("EMBEDDING", "임베딩으로 정함"), ("ASK", "묻기 기본값"), ("FALLBACK", "기본값(기타 생활)")):
    part = [r for r in clear if r["source"] == src]
    if part:
        print(f"  {label}: {sum(r['ok'] for r in part)}/{len(part)}")

# 애매하면 묻기 — 애매한 품목은 다 묻고, 분명한 품목은 판매처만 쓴 것으로(WHAT) 잘못 묻지 않아야 한다.
# 매장/배달 묻기는 식비 품목의 10% 를 넘으면 범위를 더 줄인다.
FOOD = {"외식", "카페", "배달", "마트·식자재", "편의점"}
food = [r for r in clear if set(r["expected"].split("|")) & FOOD]
print(f"\n묻기: 애매한 품목 {sum(bool(r['ask']) for r in amb)}/{len(amb)} 물음"
      f" | 분명한 품목 중 WHAT {sum(r['ask'] == 'WHAT' for r in clear)}개"
      f" · 매장/배달 {sum(r['ask'] == 'DINE_OR_DELIVERY' for r in clear)}개"
      f" (식비 품목 {len(food)}개 중 {sum(r['ask'] == 'DINE_OR_DELIVERY' for r in food)}개)")
for r in clear:
    if r["ask"]:
        print(f"  물음(분명한 품목): {r['item']:<14} {r['ask']} [{r['chips']}] 기본 {r['predicted']}")

print("\n틀린 것:")
for r in clear:
    if not r["ok"]:
        print(f"  {r['item']:<14} 정답 {r['expected']:<12} 예측 {r['predicted']:<10} 점수 {r['score']} ({r['source']})")
print("\n애매한 품목 (물어봐야 하는 것):")
for r in amb:
    asked = f"{r['ask']} [{r['chips']}]" if r["ask"] else "안 물음"
    print(f"  {r['item']:<14} 후보 {r['expected']:<22} 기본 {r['predicted']:<10} ({r['source']}) → {asked}")
scores = sorted(float(r["score"]) for r in clear)
if scores:
    print(f"\n분명한 품목 점수 범위: {scores[0]:.3f} ~ {scores[-1]:.3f} (중앙값 {scores[len(scores) // 2]:.3f})")

if args.out:
    with open(args.out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader(); w.writerows(rows)
    print("saved", args.out)
