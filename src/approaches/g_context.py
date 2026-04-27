"""방식 G: 시간-컨텍스트 증강 적응 (Time-Context Augmented Adaptive).

E의 기본 EMA를 유지하면서 **(category × time_of_day)** 셀 단위로 분리된 평균 추적.

핵심 아이디어:
  - 식비 "아침"과 "저녁"은 본질적으로 다른 분포 (커피 vs 회식)
  - 따라서 시간대별로 별도 μ, σ 유지하여 컨텍스트 인지 판정
  - 셀 데이터 부족 시 → 카테고리 전체로 fallback (cold-start 방지)

상태 구조:
  state["categories"]["식비"]["아침"]   = {"mu", "sigma", "count", "seeds"}
  state["categories"]["식비"]["점심"]   = ...
  state["categories"]["식비"]["_global"] = ... (fallback용 카테고리 종합)
  ...

판정 알고리즘:
  1. cell = (category, time_of_day)
  2. cell.count < SEED_MIN이고 _global.count < SEED_MIN_GLOBAL → 시드 수집
  3. cell.count >= SEED_MIN → cell 기반 z-score
  4. cell 부족, _global 충분 → _global 기반 z-score (fallback)

업데이트:
  - 일반: cell·_global 둘 다 EMA(α=0.15)
  - 이례: cell·_global 둘 다 약하게 (α=0.03)
  - σ는 winsorize (E와 동일)

생활고정은 C 위임 (E·F와 동일).
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from threading import Lock
from typing import Optional

from models import Expense, JudgeResponse, UserContext

from . import c_rules
from . import history as history_store

STATE_PATH = Path(__file__).parent.parent / "state" / "g_state.json"
ALPHA = 0.15
ALPHA_ANOMALY = 0.03
Z_THRESHOLD = 2.0
BASE_DELTA = 3
SEED_MIN_CELL = 3        # 셀 시드 최소
SEED_MIN_GLOBAL = 5      # 카테고리 종합 시드 최소 (cold-start fallback 활성화 기준)

CATEGORIES = ["식비", "쇼핑뷰티", "문화여가"]
TIME_OF_DAYS = ["새벽", "아침", "점심", "저녁", "밤"]

_lock = Lock()


def _empty_cell() -> dict:
    return {"mu": 0.0, "sigma": 0.0, "count": 0, "seeds": []}


def _empty_state() -> dict:
    cats = {}
    for c in CATEGORIES:
        sub = {tod: _empty_cell() for tod in TIME_OF_DAYS}
        sub["_global"] = _empty_cell()
        cats[c] = sub
    return {"categories": cats}


def load() -> dict:
    with _lock:
        if not STATE_PATH.exists():
            STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
            s = _empty_state()
            STATE_PATH.write_text(json.dumps(s, ensure_ascii=False), encoding="utf-8")
            return s
        return json.loads(STATE_PATH.read_text(encoding="utf-8"))


def save(state: dict) -> None:
    with _lock:
        STATE_PATH.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")


def reset() -> None:
    save(_empty_state())


def _seed_stats(seeds: list[float]) -> tuple[float, float]:
    n = len(seeds)
    if n == 0:
        return 0.0, 0.0
    mu = sum(seeds) / n
    var = sum((x - mu) ** 2 for x in seeds) / max(n - 1, 1)
    return mu, math.sqrt(var)


def _ema_update(cell: dict, x: float, alpha_mu: float):
    """cell의 mu·sigma 업데이트. σ는 winsorize."""
    mu = cell["mu"]
    sigma = cell["sigma"] if cell["sigma"] > 0 else max(mu * 0.3, 1.0)
    new_mu = alpha_mu * x + (1 - alpha_mu) * mu
    dev_cap = max(3.0 * sigma, mu * 0.5, 1.0)
    dev_bounded = max(-dev_cap, min(dev_cap, x - mu))
    new_sigma = math.sqrt(ALPHA * dev_bounded ** 2 + (1 - ALPHA) * sigma ** 2)
    cell["mu"] = new_mu
    cell["sigma"] = new_sigma


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    if expense.category == "생활고정":
        c_res = c_rules.judge(expense, user_context, reason)
        return JudgeResponse(
            approach="G",
            signal=c_res.signal,
            stat_delta=c_res.stat_delta,
            reasoning=f"(고정비 위임) {c_res.reasoning}",
            applied_rules=["delegated_to_fixed_cost"] + c_res.applied_rules,
        )

    state = load()
    cat = expense.category
    tod = expense.time_of_day
    if cat not in state["categories"]:
        sub = {t: _empty_cell() for t in TIME_OF_DAYS}
        sub["_global"] = _empty_cell()
        state["categories"][cat] = sub
    sub = state["categories"][cat]
    cell = sub.setdefault(tod, _empty_cell())
    glob = sub.setdefault("_global", _empty_cell())
    applied: list[str] = [f"cell:{cat}/{tod}"]

    x = expense.amount

    # ───── 시드 수집 단계 ─────
    # _global이 우선 시드 채워야 fallback 가능
    if glob["count"] < SEED_MIN_GLOBAL:
        glob["seeds"].append(x)
        glob["count"] += 1
        cell["seeds"].append(x)
        cell["count"] += 1
        if glob["count"] == SEED_MIN_GLOBAL:
            mu, sigma = _seed_stats(glob["seeds"])
            glob["mu"] = mu
            glob["sigma"] = sigma
            applied.append("global_seeded")
            msg = f"카테고리 종합 시드 완료 (μ={mu:,.0f}, σ={sigma:,.0f})"
        else:
            msg = f"카테고리 시드 수집 중 ({glob['count']}/{SEED_MIN_GLOBAL})"
            applied.append("collecting_global")
        # 셀 시드도 함께 업데이트
        if cell["count"] >= SEED_MIN_CELL:
            mu, sigma = _seed_stats(cell["seeds"])
            cell["mu"] = mu
            cell["sigma"] = sigma
        save(state)
        return JudgeResponse(
            approach="G",
            signal="gray",
            stat_delta=0,
            reasoning=msg,
            applied_rules=applied,
        )

    # ───── 본판: 셀 또는 _global 사용 결정 ─────
    if cell["count"] >= SEED_MIN_CELL and cell["mu"] > 0:
        used = "cell"
        mu, sigma = cell["mu"], cell["sigma"]
        applied.append(f"using_cell({cat}/{tod})")
    else:
        used = "global"
        mu, sigma = glob["mu"], glob["sigma"]
        applied.append("fallback_global")
        # 셀에는 시드 적립
        cell["seeds"].append(x)
        cell["count"] += 1
        if cell["count"] >= SEED_MIN_CELL:
            cmu, csig = _seed_stats(cell["seeds"])
            cell["mu"] = cmu
            cell["sigma"] = csig
            applied.append("cell_seeded")

    if sigma <= 1:
        sigma = max(mu * 0.3, 1.0)

    z = (x - mu) / sigma
    is_anomaly = False

    if z <= 0:
        signal, delta = "green", BASE_DELTA
        reasoning = f"[{used}] μ={mu:,.0f} 이하 (z={z:.2f}) · 절약"
    elif z <= 1:
        signal, delta = "green", max(BASE_DELTA // 2, 1)
        reasoning = f"[{used}] μ 약간 초과 (z={z:.2f})"
    elif z <= Z_THRESHOLD:
        signal, delta = "gray", -max(BASE_DELTA // 2, 1)
        reasoning = f"[{used}] 뚜렷 초과 (z={z:.2f})"
    else:
        is_anomaly = True
        delta = -min(int(BASE_DELTA + z * 1.5), 12)
        signal = "red"
        reasoning = f"[{used}] μ={mu:,.0f} 대비 z={z:.2f} · 이례"

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

    if reason and signal != "green":
        rc = c_rules._classify_reason(reason)
        if rc in {"경조사", "긴급", "자기투자"}:
            signal = "gray" if signal == "red" else "green"
            delta += BASE_DELTA
            applied.append(f"reason_upgrade:{rc}")
            reasoning += f" · 사유 '{rc}' 인정"

    # ───── 업데이트 ─────
    alpha_mu = ALPHA_ANOMALY if is_anomaly else ALPHA
    if cell["count"] >= SEED_MIN_CELL:
        _ema_update(cell, x, alpha_mu)
    _ema_update(glob, x, alpha_mu)
    glob["count"] += 1
    cell["count"] += 1
    save(state)

    return JudgeResponse(
        approach="G",
        signal=signal,
        stat_delta=delta,
        reasoning=reasoning,
        applied_rules=applied,
    )
