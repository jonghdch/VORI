"""방식 E: 적응형 개인 평균 + 이례 질문.

핵심 아이디어
------------
1. 튜토리얼 지출 수집 → 카테고리별 시드 평균·편차
2. 이후 지출마다 z-score 기반으로 스탯 ±
3. 평균·편차는 **지수이동평균으로 갱신** (사용자 삶에 적응)
   단, 이례(z > Z_THRESHOLD)로 판정된 지출은 평균 업데이트에서 제외
   (이례가 새로운 기준선을 오염시키는 것 방지)
4. z > 임계치 = 이례 → AI 질문 풀에 적재
5. 하루 1건 AI가 사용자에게 "왜?" 질문
6. 사유 분류 + Gemini로 최종 재판정

파라미터
--------
- ALPHA: 평균 갱신 속도 (0.15 = 신규 데이터 15% 반영)
- Z_THRESHOLD: 이례 판정 임계치 (2.0 = 상위 2.5% 정도)
- SEED_MIN: 카테고리당 시드 필요 개수 (3)
- BASE_DELTA: 기본 스탯 변동 단위

생활고정은 품목(description)별 평균이 다르므로 E가 직접 처리하지 않고
c_rules의 _judge_fixed_cost에 위임.
"""
from __future__ import annotations

import math
from typing import Optional

from models import Expense, JudgeResponse, UserContext

from . import anomaly_pool, c_rules, state_store
from . import history as history_store

ALPHA = 0.15
ALPHA_ANOMALY = 0.03  # 이례는 1/5 속도로만 반영 (반복 이례는 천천히 수용)
Z_THRESHOLD = 2.0
BASE_DELTA = 3


def _compute_seed_stats(seeds: list[float]) -> tuple[float, float]:
    """시드 리스트에서 평균·표준편차 계산."""
    n = len(seeds)
    if n == 0:
        return 0.0, 0.0
    mu = sum(seeds) / n
    var = sum((x - mu) ** 2 for x in seeds) / max(n - 1, 1)
    sigma = math.sqrt(var)
    return mu, sigma


def _update_ema(mu: float, sigma: float, x: float, alpha: float = ALPHA) -> tuple[float, float]:
    """지수이동평균으로 mu·sigma 업데이트."""
    new_mu = alpha * x + (1 - alpha) * mu
    new_sigma = math.sqrt(alpha * ((x - mu) ** 2) + (1 - alpha) * (sigma ** 2))
    return new_mu, new_sigma


def _anomaly_reasoning(expense: Expense, z: float, mu: float) -> str:
    return f"본인 평균 {mu:,.0f}원 대비 z={z:.2f} (크게 벗어남)"


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    # ───── 생활고정은 품목 다양성이 커서 z-score 의미가 약함 → C로 위임 ─────
    if expense.category == "생활고정":
        c_res = c_rules.judge(expense, user_context, reason)
        return JudgeResponse(
            approach="E",
            signal=c_res.signal,
            stat_delta=c_res.stat_delta,
            reasoning=f"(고정비 위임) {c_res.reasoning}",
            applied_rules=["delegated_to_fixed_cost"] + c_res.applied_rules,
        )

    state = state_store.load()
    cat = expense.category
    cat_state = state["categories"].setdefault(
        cat, {"mu": 0.0, "sigma": 0.0, "count": 0, "seeds": []}
    )
    applied: list[str] = []

    # ───────── 시드 수집 단계 ─────────
    if cat_state["count"] < state_store.SEED_MIN:
        cat_state["seeds"].append(expense.amount)
        cat_state["count"] += 1
        state["total_count"] = state.get("total_count", 0) + 1

        if cat_state["count"] == state_store.SEED_MIN:
            mu, sigma = _compute_seed_stats(cat_state["seeds"])
            cat_state["mu"] = mu
            cat_state["sigma"] = sigma
            msg = f"튜토리얼 수집 완료 (시드 {mu:,.0f}원 ± {sigma:,.0f}원)"
            applied.append("tutorial_finalized")
        else:
            msg = f"튜토리얼 수집 중 ({cat_state['count']}/{state_store.SEED_MIN})"
            applied.append("tutorial_collect")

        state_store.save(state)
        return JudgeResponse(
            approach="E",
            signal="gray",
            stat_delta=0,
            reasoning=msg,
            applied_rules=applied,
        )

    # ───────── 본판 판정 ─────────
    mu = cat_state["mu"] or max(expense.amount, 1.0)
    sigma = cat_state["sigma"]
    if sigma <= 1:
        # 편차가 너무 작을 때 보정 (튜토리얼 데이터가 균일할 때)
        sigma = max(mu * 0.3, 1.0)

    x = expense.amount
    z = (x - mu) / sigma

    # 스탯 변동 + 신호등 (이례일수록 감점 커짐 — z 비례 스케일링)
    is_anomaly = False
    if z <= 0:
        signal, delta = "green", BASE_DELTA
        applied.append("below_average")
        reasoning = f"평균 {mu:,.0f}원 이하 (z={z:.2f}) · 절약"
    elif z <= 1:
        signal, delta = "green", max(BASE_DELTA // 2, 1)
        applied.append("slightly_over")
        reasoning = f"평균 {mu:,.0f}원 약간 초과 (z={z:.2f})"
    elif z <= Z_THRESHOLD:
        signal, delta = "gray", -max(BASE_DELTA // 2, 1)
        applied.append("over_1sigma")
        reasoning = f"평균 뚜렷이 초과 (z={z:.2f})"
    else:  # z > Z_THRESHOLD : 이례
        is_anomaly = True
        # z 비례 감점 (z=2 → -6, z=5 → -10, z=10 → -12, 상한 -12)
        delta = -min(int(BASE_DELTA + z * 1.5), 12)
        signal = "red"
        applied.append("extreme_outlier")
        reasoning = _anomaly_reasoning(expense, z, mu)
        # 이례 풀 적재 (사유가 없을 때만; 이미 사유 있으면 즉시 반영)
        if not reason:
            priority = abs(z) * math.log10(max(x, 10))
            anomaly_pool.add(expense.model_dump(), z, priority, reasoning)
            applied.append("queued_for_ai_question")

    # 패턴 감지 (반복 소비) — z 기반으로 못 잡는 "습관성" 탐지
    repeat = history_store.count_description(expense.description)
    if repeat >= 10:
        signal = "red"
        delta = -min(BASE_DELTA * 2, 12)
        applied.append("pattern_repeat_10x")
        reasoning += f" · 같은 지출 {repeat + 1}회째 (습관성)"
    elif repeat >= 5 and signal == "green":
        signal = "gray"
        delta = -max(BASE_DELTA // 2, 1)
        applied.append("pattern_repeat_5x")
        reasoning += f" · 같은 지출 {repeat + 1}회째"

    # 사유가 이미 있으면 1단계 상향
    if reason and signal != "green":
        from .c_rules import _classify_reason

        rc = _classify_reason(reason)
        if rc in {"경조사", "긴급", "자기투자"}:
            signal = "gray" if signal == "red" else "green"
            delta += BASE_DELTA  # 감점 완화
            applied.append(f"reason_upgrade:{rc}")
            reasoning += f" · 사유 '{rc}' 인정"

    # 평균·편차 업데이트
    # - μ: 일반 α=0.15, 이례는 α=0.03 (1회성 이례로 μ 오염 방지)
    # - σ: 항상 α=0.15 EMA로 갱신, 단 편차는 winsorize (3σ or μ*0.5 상한)
    #   → 반복 이례는 σ 자라면서 수용, 1회성 극단은 제한됨
    alpha_mu = ALPHA_ANOMALY if is_anomaly else ALPHA
    new_mu = alpha_mu * x + (1 - alpha_mu) * mu

    dev_cap = max(3.0 * sigma, mu * 0.5, 1.0)
    dev_bounded = max(-dev_cap, min(dev_cap, x - mu))
    new_sigma = math.sqrt(ALPHA * dev_bounded ** 2 + (1 - ALPHA) * sigma ** 2)

    cat_state["mu"] = new_mu
    cat_state["sigma"] = new_sigma
    cat_state["count"] += 1
    if is_anomaly:
        applied.append(f"mu_slow(α={ALPHA_ANOMALY}) · sigma_winsorized")
    state["total_count"] = state.get("total_count", 0) + 1
    state_store.save(state)

    return JudgeResponse(
        approach="E",
        signal=signal,
        stat_delta=delta,
        reasoning=reasoning,
        applied_rules=applied,
    )
