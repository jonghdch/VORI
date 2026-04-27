"""방식 F: 계층형 강건 적응 (Hierarchical Robust Adaptive).

E의 세 가지 약점을 개선:
  1. 시드 부족 → SEED_MIN 5로 상향 + 서브버킷별 개별 시드
  2. σ 폭주 → 표준편차 대신 MAD (Median Absolute Deviation) 사용
  3. 쇼핑뷰티 품목 다양성 → 카테고리 내 **금액 tier 서브버킷** 자동 분류
       (예: 쇼핑뷰티 = [소액생필품 < 20k / 중액의류 20-80k / 대액명품 >80k])

핵심 수식 — Modified z-score (Iglewicz & Hoaglin, 1993):
  z_F = 0.6745 × (x − median) / MAD
  이상치에 강건 (σ가 쉽게 오염되지 않음)

적응형 업데이트:
  - Weighted running quantile 대신 최근 N=50건 샘플 슬라이딩 윈도우
  - median, MAD 재계산 (O(N log N), N 작아서 부담 없음)
  - 이례(z_F > 2) 샘플은 창 추가에서 제외하여 오염 방지

생활고정은 C로 위임 (E와 동일 정책).
"""
from __future__ import annotations

import statistics
from collections import deque
from pathlib import Path
from threading import Lock
from typing import Optional
import json

from models import Expense, JudgeResponse, UserContext

from . import c_rules

STATE_PATH = Path(__file__).parent.parent / "state" / "f_state.json"
WINDOW = 50  # 슬라이딩 윈도우 크기
SEED_MIN = 7  # E(3)보다 많이 — 편차 안정화
Z_THRESHOLD = 2.0
BASE_DELTA = 3

# v3: 서브버킷 제거. E처럼 단일 분포로 처리하되 MAD 기반 robust 통계.
# 버킷 경계가 페르소나별 의미가 달라 문제 → 개인 샘플로 동적 판단하는 게 나음.
BUCKETS = {
    "식비": [("all", 0, 10**9)],
    "쇼핑뷰티": [("all", 0, 10**9)],
    "문화여가": [("all", 0, 10**9)],
}

_lock = Lock()


def _empty_state() -> dict:
    cats = {}
    for cat, buckets in BUCKETS.items():
        cats[cat] = {name: {"samples": [], "median": 0.0, "mad": 0.0, "count": 0} for name, _, _ in buckets}
    return {"categories": cats}


def _load() -> dict:
    with _lock:
        if not STATE_PATH.exists():
            STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
            s = _empty_state()
            STATE_PATH.write_text(json.dumps(s, ensure_ascii=False), encoding="utf-8")
            return s
        return json.loads(STATE_PATH.read_text(encoding="utf-8"))


def _save(state: dict) -> None:
    with _lock:
        STATE_PATH.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")


def reset() -> None:
    _save(_empty_state())


def _bucket_name(category: str, amount: int) -> str:
    buckets = BUCKETS.get(category, [])
    for name, lo, hi in buckets:
        if lo <= amount < hi:
            return name
    return buckets[-1][0] if buckets else "default"


def _robust_stats(samples: list[float]) -> tuple[float, float]:
    """median과 MAD(×1.4826) 계산. tight 클러스터 과민 방지 위해 하한 크게."""
    if not samples:
        return 0.0, 0.0
    med = statistics.median(samples)
    abs_dev = [abs(x - med) for x in samples]
    mad = statistics.median(abs_dev) * 1.4826  # robust σ 추정
    # 하한선: median의 50% (동질한 시드 클러스터가 과민 반응하는 것 방지)
    min_mad = max(med * 0.5, 500.0)
    if mad < min_mad:
        mad = min_mad
    return float(med), float(mad)


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    # ───── 생활고정은 C로 위임 (E와 동일 정책) ─────
    if expense.category == "생활고정":
        c_res = c_rules.judge(expense, user_context, reason)
        return JudgeResponse(
            approach="F",
            signal=c_res.signal,
            stat_delta=c_res.stat_delta,
            reasoning=f"(고정비 위임) {c_res.reasoning}",
            applied_rules=["delegated_to_fixed_cost"] + c_res.applied_rules,
        )

    state = _load()
    cat = expense.category
    bucket_name = _bucket_name(cat, expense.amount)
    cat_state = state["categories"].setdefault(cat, {})
    bucket = cat_state.setdefault(bucket_name, {"samples": [], "median": 0.0, "mad": 0.0, "count": 0})
    applied: list[str] = [f"bucket:{bucket_name}"]

    x = expense.amount

    # ───── 시드 수집 단계 ─────
    if bucket["count"] < SEED_MIN:
        bucket["samples"].append(x)
        bucket["count"] += 1
        if bucket["count"] == SEED_MIN:
            med, mad = _robust_stats(bucket["samples"])
            bucket["median"] = med
            bucket["mad"] = mad
            msg = f"[{bucket_name}] 시드 완료: median={med:,.0f}, MAD={mad:,.0f}"
            applied.append("bucket_seeded")
        else:
            msg = f"[{bucket_name}] 시드 수집 중 ({bucket['count']}/{SEED_MIN})"
            applied.append("bucket_collecting")
        _save(state)
        return JudgeResponse(
            approach="F",
            signal="gray",
            stat_delta=0,
            reasoning=msg,
            applied_rules=applied,
        )

    # ───── 본판: modified z-score ─────
    med = bucket["median"] or 1
    mad = bucket["mad"] or max(med * 0.15, 1)
    z = 0.6745 * (x - med) / mad
    is_anomaly = False

    if z <= 0:
        signal, delta = "green", BASE_DELTA
        reasoning = f"[{bucket_name}] median {med:,.0f} 이하 · 절약 (z_F={z:.2f})"
        applied.append("below_median")
    elif z <= 1:
        signal, delta = "green", max(BASE_DELTA // 2, 1)
        reasoning = f"[{bucket_name}] median 약간 초과 (z_F={z:.2f})"
        applied.append("slightly_over")
    elif z <= Z_THRESHOLD:
        signal, delta = "gray", -max(BASE_DELTA // 2, 1)
        reasoning = f"[{bucket_name}] median 뚜렷 초과 (z_F={z:.2f})"
        applied.append("over_1mad")
    else:
        is_anomaly = True
        delta = -min(int(BASE_DELTA + z * 1.5), 12)
        signal = "red"
        reasoning = f"[{bucket_name}] median={med:,.0f} 대비 z_F={z:.2f} · 이례"
        applied.append("extreme_outlier")

    # 사유 반영
    if reason and signal != "green":
        rc = c_rules._classify_reason(reason)
        if rc in {"경조사", "긴급", "자기투자"}:
            signal = "gray" if signal == "red" else "green"
            delta += BASE_DELTA
            applied.append(f"reason_upgrade:{rc}")
            reasoning += f" · 사유 '{rc}' 인정"

    # 윈도우 업데이트 — 이례가 아닌 경우에만 (오염 방지)
    if not is_anomaly:
        samples: list = bucket["samples"]
        samples.append(x)
        if len(samples) > WINDOW:
            samples.pop(0)
        med2, mad2 = _robust_stats(samples)
        bucket["median"] = med2
        bucket["mad"] = mad2
    bucket["count"] += 1
    _save(state)

    return JudgeResponse(
        approach="F",
        signal=signal,
        stat_delta=delta,
        reasoning=reasoning,
        applied_rules=applied,
    )
