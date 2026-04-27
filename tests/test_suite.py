"""다양한 소비 시나리오를 돌려 B/C/D 판정을 기대값과 비교하는 테스트 스위트.

사용: python3 test_suite.py (서버가 localhost:8000 가동 중이어야 함)
"""
from __future__ import annotations

import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8000"

STUDENT = {
    "job": "대학생",
    "monthly_income": 500000,
    "total_assets": 3000000,
    "category_avg": {"식비": 200000, "쇼핑뷰티": 100000, "문화여가": 50000, "생활고정": 400000},
}

WORKER = {
    "job": "직장인",
    "monthly_income": 3000000,
    "total_assets": 10000000,
    "category_avg": {"식비": 400000, "쇼핑뷰티": 200000, "문화여가": 150000, "생활고정": 1200000},
}

# 테스트 케이스: (이름, 기대값, 맥락, 지출, 사유)
CASES = [
    # ─── 식비 ───
    ("식비-편의점김밥",        "green",  STUDENT, {"description": "편의점 삼각김밥", "amount": 2500,   "category": "식비",     "time_of_day": "점심"}, None),
    ("식비-학식",              "green",  STUDENT, {"description": "학식",             "amount": 5000,   "category": "식비",     "time_of_day": "점심"}, None),
    ("식비-김밥천국",          "green",  STUDENT, {"description": "김밥천국 점심",    "amount": 9000,   "category": "식비",     "time_of_day": "점심"}, None),
    ("식비-평일저녁삼겹살",    "gray",   WORKER, {"description": "저녁 삼겹살 회식",      "amount": 35000,  "category": "식비",     "time_of_day": "저녁"}, None),
    ("식비-호텔뷔페",          "red",    STUDENT, {"description": "호텔 뷔페",        "amount": 150000, "category": "식비",     "time_of_day": "저녁"}, None),
    ("식비-새벽야식",          "red",    STUDENT, {"description": "배달 치킨",        "amount": 25000,  "category": "식비",     "time_of_day": "새벽"}, None),
    ("식비-스벅커피아침",      "green",  STUDENT, {"description": "스타벅스 아메리카노","amount": 5000,   "category": "식비",     "time_of_day": "아침"}, None),
    ("식비-디저트카페",        "gray",   STUDENT, {"description": "디저트 카페",      "amount": 12000,  "category": "식비",     "time_of_day": "저녁"}, None),
    ("식비-생일파티",          "red",    STUDENT, {"description": "생일 파티 음식",   "amount": 100000, "category": "식비",     "time_of_day": "저녁"}, None),
    ("식비-직장인점심",        "green",  WORKER,  {"description": "회사 근처 점심",   "amount": 10000,  "category": "식비",     "time_of_day": "점심"}, None),

    # ─── 쇼핑뷰티 ───
    ("쇼핑-양말",              "green",  STUDENT, {"description": "생필품 양말",      "amount": 5000,   "category": "쇼핑뷰티", "time_of_day": "점심"}, None),
    ("쇼핑-기초화장품",        "green",  STUDENT, {"description": "기초 스킨케어",    "amount": 30000,  "category": "쇼핑뷰티", "time_of_day": "저녁"}, None),
    ("쇼핑-세일코트",          "gray",   STUDENT, {"description": "세일 코트",        "amount": 80000,  "category": "쇼핑뷰티", "time_of_day": "저녁"}, None),
    ("쇼핑-명품지갑충동",      "red",    STUDENT, {"description": "명품 지갑 충동",   "amount": 500000, "category": "쇼핑뷰티", "time_of_day": "밤"},   None),
    ("쇼핑-새벽홈쇼핑",        "red",    STUDENT, {"description": "새벽 홈쇼핑 화장품","amount": 200000, "category": "쇼핑뷰티", "time_of_day": "새벽"}, None),
    ("쇼핑-필요한운동화세일",  "gray",   STUDENT, {"description": "세일 운동화",      "amount": 70000,  "category": "쇼핑뷰티", "time_of_day": "저녁"}, None),

    # ─── 문화여가 ───
    ("여가-영화",              "green",  STUDENT, {"description": "영화 티켓",        "amount": 13000,  "category": "문화여가", "time_of_day": "저녁"}, None),
    ("여가-도서",              "green",  STUDENT, {"description": "서점 신간",        "amount": 18000,  "category": "문화여가", "time_of_day": "저녁"}, None),
    ("여가-도서관",            "green",  STUDENT, {"description": "도서관 대출",      "amount": 0,      "category": "문화여가", "time_of_day": "점심"}, None),
    ("여가-헬스장",            "gray",   STUDENT, {"description": "헬스장 월 이용권", "amount": 50000,  "category": "문화여가", "time_of_day": "저녁"}, None),
    ("여가-콘서트",            "gray",   STUDENT, {"description": "콘서트 티켓",      "amount": 120000, "category": "문화여가", "time_of_day": "저녁"}, None),
    ("여가-유흥주점",          "red",    STUDENT, {"description": "유흥주점",         "amount": 200000, "category": "문화여가", "time_of_day": "밤"},   None),
    ("여가-도박게임결제",      "red",    STUDENT, {"description": "도박성 게임 결제", "amount": 100000, "category": "문화여가", "time_of_day": "새벽"}, None),
    ("여가-자격증교재",        "green",  STUDENT, {"description": "자격증 교재",      "amount": 30000,  "category": "문화여가", "time_of_day": "점심"}, "자격증 공부"),
    ("여가-온라인강의",        "green",  STUDENT, {"description": "온라인 강의",      "amount": 50000,  "category": "문화여가", "time_of_day": "점심"}, "자기투자"),

    # ─── 생활고정 ───
    ("고정-월세",              "green",  WORKER, {"description": "월세",            "amount": 500000, "category": "생활고정", "time_of_day": "아침"}, None),
    ("고정-공과금",            "green",  STUDENT, {"description": "전기 수도 공과금", "amount": 80000,  "category": "생활고정", "time_of_day": "아침"}, None),
    ("고정-통신비",            "green",  STUDENT, {"description": "통신비",          "amount": 70000,  "category": "생활고정", "time_of_day": "아침"}, None),
    ("고정-넷플릭스",          "green",  STUDENT, {"description": "넷플릭스 구독",    "amount": 17000,  "category": "생활고정", "time_of_day": "아침"}, None),

    # ─── 사유 기반 ───
    ("사유-친구결혼식",        "gray",   STUDENT, {"description": "친구 결혼식 축의금","amount": 100000, "category": "문화여가", "time_of_day": "점심"}, "친한 친구 결혼식 축의금"),
    ("사유-응급실택시",        "green",  WORKER,  {"description": "응급실 택시",      "amount": 50000,  "category": "생활고정", "time_of_day": "새벽"}, "어머니 응급실 이송"),
    ("사유-팀원커피팀장",      "gray",   WORKER,  {"description": "팀원들 커피",      "amount": 30000,  "category": "식비",     "time_of_day": "점심"}, "팀 사기 진작용"),
    ("사유-학원비자기투자",    "green",  WORKER,  {"description": "영어 학원비",      "amount": 150000, "category": "문화여가", "time_of_day": "저녁"}, "취업 대비 자기투자"),
]


def post(path: str, body: dict) -> dict:
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def delete(path: str) -> None:
    req = urllib.request.Request(BASE + path, method="DELETE")
    with urllib.request.urlopen(req) as resp:
        resp.read()


def main() -> int:
    header = f"{'케이스':<24} {'기대':6} {'B':7} {'C':7} {'D':7}  결과"
    print(header)
    print("─" * len(header))
    stats = {"B": [0, 0], "C": [0, 0], "D": [0, 0]}  # [맞음, 전체]
    mismatches: list[tuple[str, str, dict]] = []
    for name, expected, user_ctx, expense, reason in CASES:
        # 각 케이스마다 히스토리 초기화 — 단일 지출 판정 순수성 확보
        delete("/history")
        body = {"expense": expense, "user_context": user_ctx, "reason": reason}
        res = post("/judge-all", body)
        by_approach = {r["approach"]: r for r in res}
        row = [name, expected]
        mark = []
        for app in ("B", "C", "D"):
            got = by_approach[app]["signal"]
            stats[app][1] += 1
            if got == expected:
                stats[app][0] += 1
                mark.append(f"{got}✓")
            else:
                mark.append(f"{got}✗")
        status = "OK" if all(by_approach[a]["signal"] == expected for a in ("B", "C", "D")) else "mismatch"
        if status == "mismatch":
            mismatches.append((name, expected, by_approach))
        print(f"{name:<24} {expected:<6} {mark[0]:<7} {mark[1]:<7} {mark[2]:<7}  {status}")

    print()
    print("─" * 48)
    for app in ("B", "C", "D"):
        ok, total = stats[app]
        pct = (ok / total * 100) if total else 0
        print(f"{app}: {ok}/{total}  ({pct:.1f}%)")

    if mismatches:
        print()
        print("=== 불일치 상세 ===")
        for name, expected, by_app in mismatches:
            print(f"\n[{name}] 기대={expected}")
            for a in ("B", "C", "D"):
                r = by_app[a]
                print(f"  {a}: {r['signal']}  | {r['reasoning']}")

    return 0 if not mismatches else 1


if __name__ == "__main__":
    sys.exit(main())
