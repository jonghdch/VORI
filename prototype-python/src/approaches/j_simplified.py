"""방식 J: I의 단순화 버전.

I에서 시뮬·이론 양면으로 효과가 의문인 컴포넌트 제거:
  - 확신도 가중 (시뮬에서 빠르게 1.0 도달, 효과 미측정)
  - 카테고리 예산 풀 / pace 룰 (사용자 목표 입력 UI 없으면 무력)
  - 사용자 목표 통합 (UserContext.category_avg는 정확한 목표 아닐 수 있음)

유지하는 핵심:
  - 시간대 × 요일 셀 (G의 핵심)
  - 빈도 z-score (H의 핵심)
  - **시간 감쇠 EMA** (장기 사용·sparse 시 critical)
  - **베이지안 amount prior** (cold-start)
  - **베이지안 frequency prior** (새 description)

→ 코드 단순화, 튜닝 파라미터 절반, 효과 동등 목표.
"""
from __future__ import annotations

import json
import math
import statistics
from datetime import date as date_t, datetime, timedelta
from pathlib import Path
from threading import Lock
from typing import Optional

from models import Expense, JudgeResponse, UserContext

from . import c_rules

STATE_PATH = Path(__file__).parent.parent / "state" / "j_state.json"

# 파라미터 (I 대비 4개 → 9개로 줄어듦, pace·confidence 관련 5개 제거)
TAU_DAYS = 30
PRIOR_N = 5
PRIOR_FREQ_WEEKLY = 1.0
PRIOR_FREQ_SIGMA = 1.5
SEED_MIN_GLOBAL = 5
Z_AMT_THRESHOLD = 2.0
Z_FREQ_GRAY = 2.0
Z_FREQ_RED = 3.0
BASE_DELTA = 3

CATEGORIES = ["식비", "쇼핑뷰티", "문화여가"]
TIME_OF_DAYS = ["새벽", "아침", "점심", "저녁", "밤"]
DAY_TYPES = ["평일", "주말"]

_lock = Lock()


def _empty_cell() -> dict:
    return {"mu": 0.0, "sigma": 0.0, "count": 0, "seeds": [], "last_update_date": None}


def _empty_state() -> dict:
    cats = {}
    for c in CATEGORIES:
        sub = {f"{tod}_{dt}": _empty_cell() for tod in TIME_OF_DAYS for dt in DAY_TYPES}
        sub["_global"] = _empty_cell()
        cats[c] = sub
    return {"categories": cats, "desc_freq": {}}


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


def _is_weekend(date_str: str) -> bool:
    try:
        return datetime.fromisoformat(date_str).date().weekday() >= 5
    except Exception:
        return False


def _days_between(d_a: Optional[str], d_b: str) -> int:
    if not d_a:
        return 0
    try:
        return abs((datetime.fromisoformat(d_b).date() - datetime.fromisoformat(d_a).date()).days)
    except Exception:
        return 0


def _time_decayed_alpha(days_since: int) -> float:
    base = 0.15
    extra = (1 - math.exp(-days_since / TAU_DAYS)) * 0.30
    return min(0.50, base + extra)


def _ema_update(cell: dict, x: float, alpha: float) -> None:
    mu = cell["mu"]
    sigma = cell["sigma"] if cell["sigma"] > 0 else max(mu * 0.3, 1.0)
    new_mu = alpha * x + (1 - alpha) * mu
    dev_cap = max(3.0 * sigma, mu * 0.5, 1.0)
    dev_b = max(-dev_cap, min(dev_cap, x - mu))
    new_sigma = math.sqrt(0.15 * dev_b ** 2 + 0.85 * sigma ** 2)
    cell["mu"] = new_mu
    cell["sigma"] = new_sigma


def _bayesian_z(x: float, cell: dict, prior_mu: float, prior_sigma: float) -> tuple[float, float, float]:
    n = cell["count"]
    if prior_sigma <= 0:
        prior_sigma = max(prior_mu * 0.3, 1.0)
    if n == 0 or cell["mu"] == 0:
        return (x - prior_mu) / max(prior_sigma, 1.0), prior_mu, prior_sigma
    w_obs = n / (n + PRIOR_N)
    w_pri = PRIOR_N / (n + PRIOR_N)
    post_mu = w_obs * cell["mu"] + w_pri * prior_mu
    cell_sigma = cell["sigma"] if cell["sigma"] > 0 else max(cell["mu"] * 0.3, 1.0)
    post_sigma = max(w_obs * cell_sigma + w_pri * prior_sigma, 1.0)
    return (x - post_mu) / post_sigma, post_mu, post_sigma


def _signal_from_z(z: float, threshold_red: float = Z_AMT_THRESHOLD) -> str:
    if z <= 1:
        return "green"
    if z <= threshold_red:
        return "gray"
    return "red"


def _compute_freq_z(events: list[str], current_date: str) -> tuple[float, dict]:
    cur_d = datetime.fromisoformat(current_date).date()
    cutoff = (cur_d - timedelta(days=28)).isoformat()
    recent = [e for e in events if e >= cutoff]
    info = {"events_count": len(recent)}

    weeks = []
    for w in range(4):
        ws = (cur_d - timedelta(days=(w + 1) * 7)).isoformat()
        we = (cur_d - timedelta(days=w * 7)).isoformat()
        c = sum(1 for e in recent if ws <= e < we)
        weeks.append(c)

    if len(recent) < 4:
        n = len(recent)
        post_mu = (n * statistics.mean(weeks) + PRIOR_N * PRIOR_FREQ_WEEKLY) / (n + PRIOR_N) if n > 0 else PRIOR_FREQ_WEEKLY
        post_sigma = PRIOR_FREQ_SIGMA
    else:
        mu_w = statistics.mean(weeks)
        sig_w = statistics.stdev(weeks) if len(set(weeks)) > 1 else max(mu_w * 0.3, 0.5)
        sig_w = max(sig_w, 0.5)
        n = len(recent)
        w_obs = n / (n + PRIOR_N)
        post_mu = w_obs * mu_w + (1 - w_obs) * PRIOR_FREQ_WEEKLY
        post_sigma = w_obs * sig_w + (1 - w_obs) * PRIOR_FREQ_SIGMA

    week_start = (cur_d - timedelta(days=7)).isoformat()
    cur_week = sum(1 for e in recent if e >= week_start) + 1
    z = (cur_week - post_mu) / max(post_sigma, 0.5)
    info.update({"current_week": cur_week, "post_mu": post_mu, "post_sigma": post_sigma, "z_freq": z})
    return z, info


SIG_ORDER = {"green": 0, "gray": 1, "red": 2}


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    if expense.category == "생활고정":
        c_res = c_rules.judge(expense, user_context, reason)
        return JudgeResponse(
            approach="J", signal=c_res.signal, stat_delta=c_res.stat_delta,
            reasoning=f"(고정비 위임) {c_res.reasoning}",
            applied_rules=["delegated"] + c_res.applied_rules,
        )

    state = load()
    cat = expense.category
    tod = expense.time_of_day
    exp_date = expense.date or date_t.today().isoformat()
    day_type = "주말" if _is_weekend(exp_date) else "평일"
    cell_key = f"{tod}_{day_type}"

    if cat not in state["categories"]:
        sub = {f"{t}_{d}": _empty_cell() for t in TIME_OF_DAYS for d in DAY_TYPES}
        sub["_global"] = _empty_cell()
        state["categories"][cat] = sub
    sub = state["categories"][cat]
    cell = sub.setdefault(cell_key, _empty_cell())
    glob = sub.setdefault("_global", _empty_cell())
    desc_freq = state.setdefault("desc_freq", {})
    df = desc_freq.setdefault(expense.description, {"events": []})
    applied: list[str] = [f"cell:{cat}/{cell_key}"]
    x = expense.amount

    # 시드 단계
    if glob["count"] < SEED_MIN_GLOBAL:
        glob["seeds"].append(x); glob["count"] += 1
        cell["seeds"].append(x); cell["count"] += 1
        glob["last_update_date"] = exp_date
        cell["last_update_date"] = exp_date
        if glob["count"] == SEED_MIN_GLOBAL:
            mu = sum(glob["seeds"]) / len(glob["seeds"])
            var = sum((y - mu) ** 2 for y in glob["seeds"]) / max(len(glob["seeds"]) - 1, 1)
            glob["mu"] = mu
            glob["sigma"] = math.sqrt(var)
            msg = f"시드 완료 (μ={mu:,.0f})"
        else:
            msg = f"시드 수집 중 ({glob['count']}/{SEED_MIN_GLOBAL})"
        df["events"].append(exp_date)
        save(state)
        return JudgeResponse(approach="J", signal="gray", stat_delta=0, reasoning=msg, applied_rules=applied)

    # 베이지안 z-score
    z_amount, post_mu, post_sigma = _bayesian_z(x, cell, glob["mu"], glob["sigma"])
    amount_signal = _signal_from_z(z_amount)
    applied.append(f"z_amt={z_amount:.2f}")

    # 빈도 z-score (베이지안)
    z_freq, freq_info = _compute_freq_z(df["events"], exp_date)
    freq_signal = _signal_from_z(z_freq, threshold_red=Z_FREQ_RED) if freq_info["events_count"] >= 1 else None

    # 종합
    signal = amount_signal
    z_for_delta = z_amount
    if freq_signal:
        if z_freq > Z_FREQ_RED:
            if amount_signal != "green":
                signal = "red"
                z_for_delta = max(z_amount, z_freq)
            else:
                signal = "gray"
                z_for_delta = z_freq
        elif z_freq > Z_FREQ_GRAY and amount_signal == "green":
            signal = "gray"
            z_for_delta = z_freq

    # delta (확신도 가중 없음)
    if signal == "green":
        delta = BASE_DELTA if z_amount <= 0 else max(BASE_DELTA // 2, 1)
    elif signal == "gray":
        delta = -max(BASE_DELTA // 2, 1)
    else:
        delta = -min(int(BASE_DELTA + z_for_delta * 1.5), 12)

    # 사유
    if reason and signal != "green":
        rc = c_rules._classify_reason(reason)
        if rc in {"경조사", "긴급", "자기투자"}:
            signal = "gray" if signal == "red" else "green"
            delta += BASE_DELTA
            applied.append(f"reason:{rc}")

    parts = [f"z_amt={z_amount:.2f} (μ={post_mu:,.0f}±{post_sigma:,.0f})"]
    if freq_signal:
        parts.append(f"이번주 {freq_info['current_week']}회 (post={freq_info['post_mu']:.1f}) z_freq={z_freq:.2f}")
    if signal == "red":
        parts.append("★이례")
    reasoning = " · ".join(parts)

    # 시간 감쇠 EMA
    days_since = _days_between(cell["last_update_date"], exp_date)
    alpha = _time_decayed_alpha(days_since)
    if signal == "red":
        alpha = min(alpha, 0.05)
    _ema_update(cell, x, alpha)
    _ema_update(glob, x, alpha)
    cell["last_update_date"] = exp_date
    glob["last_update_date"] = exp_date
    cell["count"] += 1
    glob["count"] += 1

    df["events"].append(exp_date)
    cutoff = (datetime.fromisoformat(exp_date).date() - timedelta(days=35)).isoformat()
    df["events"] = [e for e in df["events"] if e >= cutoff]

    save(state)

    return JudgeResponse(approach="J", signal=signal, stat_delta=delta, reasoning=reasoning, applied_rules=applied)
