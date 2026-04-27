"""방식 H: G + 빈도 적응 + 요일 차원.

G 대비 개선:
  1. **요일 차원 추가**: (category × time_of_day × weekday/weekend) 셀
  2. **빈도 z-score**: description별 주간 발생 빈도 EMA로 학습 → 이상 빈도 탐지
  3. **5/10회 hardcode 룰 제거**: 빈도 z-score로 자연스럽게 대체

수식:
  - amount_z = G와 동일 (cell μ·σ)
  - freq_z = (이번주 발생수 - μ_weekly) / σ_weekly
  - 최종 = worst(amount_signal, freq_signal)

상태 구조:
  state["categories"]["식비"]["아침_평일"] = {μ, σ, count, seeds}
  state["categories"]["식비"]["아침_주말"] = ...
  state["categories"]["식비"]["_global"] = ...
  state["desc_freq"]["스타벅스 아메리카노"] = {events: [...], μ_weekly, σ_weekly}
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

STATE_PATH = Path(__file__).parent.parent / "state" / "h_state.json"
ALPHA = 0.15
ALPHA_ANOMALY = 0.03
Z_THRESHOLD = 2.0
BASE_DELTA = 3
SEED_MIN_CELL = 3
SEED_MIN_GLOBAL = 5
FREQ_WINDOW_DAYS = 28        # 빈도 분석 윈도우
FREQ_MIN_EVENTS = 4          # 빈도 분석 활성화 최소 이벤트 수
FREQ_Z_RED = 3.0             # 빈도 단독 red 임계 (amount보다 보수적)
FREQ_Z_GRAY = 2.0            # 빈도 gray 임계

CATEGORIES = ["식비", "쇼핑뷰티", "문화여가"]
TIME_OF_DAYS = ["새벽", "아침", "점심", "저녁", "밤"]
DAY_TYPES = ["평일", "주말"]

_lock = Lock()


def _empty_cell() -> dict:
    return {"mu": 0.0, "sigma": 0.0, "count": 0, "seeds": []}


def _empty_state() -> dict:
    cats = {}
    for c in CATEGORIES:
        sub = {}
        for tod in TIME_OF_DAYS:
            for dt in DAY_TYPES:
                sub[f"{tod}_{dt}"] = _empty_cell()
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


def _seed_stats(seeds: list[float]) -> tuple[float, float]:
    n = len(seeds)
    if n == 0:
        return 0.0, 0.0
    mu = sum(seeds) / n
    var = sum((x - mu) ** 2 for x in seeds) / max(n - 1, 1)
    return mu, math.sqrt(var)


def _ema_update(cell: dict, x: float, alpha_mu: float):
    mu = cell["mu"]
    sigma = cell["sigma"] if cell["sigma"] > 0 else max(mu * 0.3, 1.0)
    new_mu = alpha_mu * x + (1 - alpha_mu) * mu
    dev_cap = max(3.0 * sigma, mu * 0.5, 1.0)
    dev_bounded = max(-dev_cap, min(dev_cap, x - mu))
    new_sigma = math.sqrt(ALPHA * dev_bounded ** 2 + (1 - ALPHA) * sigma ** 2)
    cell["mu"] = new_mu
    cell["sigma"] = new_sigma


def _compute_freq_z(events: list[str], current_date: str) -> tuple[float, dict]:
    """description의 주간 빈도 z-score 계산. (z, info dict) 반환."""
    cur_d = datetime.fromisoformat(current_date).date()
    cutoff = (cur_d - timedelta(days=FREQ_WINDOW_DAYS)).isoformat()
    recent = [e for e in events if e >= cutoff]
    info = {"events_count": len(recent), "weekly_counts": []}

    if len(recent) < FREQ_MIN_EVENTS:
        return 0.0, info

    # 4주간 주별 카운트
    weeks = []
    for w in range(4):
        ws = (cur_d - timedelta(days=(w + 1) * 7)).isoformat()
        we = (cur_d - timedelta(days=w * 7)).isoformat()
        c = sum(1 for e in recent if ws <= e < we)
        weeks.append(c)
    mu_w = statistics.mean(weeks)
    if len(set(weeks)) > 1:
        sig_w = statistics.stdev(weeks)
    else:
        sig_w = 0.0
    if sig_w < 0.5:
        sig_w = max(mu_w * 0.3, 0.5)

    # 이번 주 카운트 + 현재 지출 (1)
    week_start = (cur_d - timedelta(days=7)).isoformat()
    cur_week = sum(1 for e in recent if e >= week_start) + 1
    z = (cur_week - mu_w) / sig_w
    info.update({"mu_weekly": mu_w, "sigma_weekly": sig_w, "current_week_count": cur_week, "z_freq": z})
    return z, info


def _signal_from_z(z: float) -> str:
    if z <= 1:
        return "green"
    if z <= Z_THRESHOLD:
        return "gray"
    return "red"


def _delta_from_z(z: float, signal: str) -> int:
    if signal == "green":
        return BASE_DELTA if z <= 0 else max(BASE_DELTA // 2, 1)
    if signal == "gray":
        return -max(BASE_DELTA // 2, 1)
    # red
    return -min(int(BASE_DELTA + z * 1.5), 12)


SIG_ORDER = {"green": 0, "gray": 1, "red": 2}


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    # 생활고정은 C 위임 (E·G와 동일)
    if expense.category == "생활고정":
        c_res = c_rules.judge(expense, user_context, reason)
        return JudgeResponse(
            approach="H",
            signal=c_res.signal,
            stat_delta=c_res.stat_delta,
            reasoning=f"(고정비 위임) {c_res.reasoning}",
            applied_rules=["delegated_to_fixed_cost"] + c_res.applied_rules,
        )

    state = load()
    cat = expense.category
    tod = expense.time_of_day
    exp_date = expense.date or date_t.today().isoformat()
    day_type = "주말" if _is_weekend(exp_date) else "평일"
    cell_key = f"{tod}_{day_type}"

    if cat not in state["categories"]:
        sub = {}
        for tod_ in TIME_OF_DAYS:
            for dt_ in DAY_TYPES:
                sub[f"{tod_}_{dt_}"] = _empty_cell()
        sub["_global"] = _empty_cell()
        state["categories"][cat] = sub
    sub = state["categories"][cat]
    cell = sub.setdefault(cell_key, _empty_cell())
    glob = sub.setdefault("_global", _empty_cell())
    desc_freq = state.setdefault("desc_freq", {})
    df = desc_freq.setdefault(expense.description, {"events": [], "mu_weekly": 0.0, "sigma_weekly": 0.0})
    applied: list[str] = [f"cell:{cat}/{cell_key}"]

    x = expense.amount

    # ───── 시드 단계 ─────
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
        if cell["count"] >= SEED_MIN_CELL:
            cm, cs = _seed_stats(cell["seeds"])
            cell["mu"] = cm
            cell["sigma"] = cs
        df["events"].append(exp_date)
        save(state)
        return JudgeResponse(
            approach="H",
            signal="gray",
            stat_delta=0,
            reasoning=msg,
            applied_rules=applied,
        )

    # ───── 본판: 금액 z-score ─────
    if cell["count"] >= SEED_MIN_CELL and cell["mu"] > 0:
        used = f"cell({cell_key})"
        mu, sigma = cell["mu"], cell["sigma"]
    else:
        used = "global"
        mu, sigma = glob["mu"], glob["sigma"]
        cell["seeds"].append(x)
        cell["count"] += 1
        if cell["count"] >= SEED_MIN_CELL:
            cm, cs = _seed_stats(cell["seeds"])
            cell["mu"] = cm
            cell["sigma"] = cs

    if sigma <= 1:
        sigma = max(mu * 0.3, 1.0)

    z_amount = (x - mu) / sigma
    amount_signal = _signal_from_z(z_amount)
    is_anomaly_amount = amount_signal == "red"
    applied.append(f"z_amount={z_amount:.2f}")

    # ───── 빈도 z-score ─────
    z_freq, freq_info = _compute_freq_z(df["events"], exp_date)
    freq_signal = None
    if freq_info["events_count"] >= FREQ_MIN_EVENTS:
        freq_signal = _signal_from_z(z_freq)
        applied.append(f"z_freq={z_freq:.2f}")
        df["mu_weekly"] = freq_info.get("mu_weekly", 0.0)
        df["sigma_weekly"] = freq_info.get("sigma_weekly", 0.0)

    # ───── 종합 판정: 빈도는 보조 신호로 사용 ─────
    # 원칙: 빈도가 단독으로 red 만들지 않음 (false positive 방지)
    #  - z_freq > 3.0 AND amount_signal != green → red 가능
    #  - z_freq > 2.0 AND amount_signal == green → gray로 하향
    #  - 빈도가 정상이면 amount 판정 그대로
    signal = amount_signal
    z_for_delta = z_amount
    freq_modifier = "no_freq_data" if freq_signal is None else "freq_normal"
    if freq_signal:
        if z_freq > FREQ_Z_RED:
            # 매우 강한 빈도 이상 + 금액도 의심스러우면 red
            if amount_signal != "green":
                signal = "red"
                z_for_delta = max(z_amount, z_freq)
                freq_modifier = "freq_extreme"
            else:
                # 금액은 정상이나 빈도 매우 이상 → gray (downgrade)
                signal = "gray"
                z_for_delta = z_freq
                freq_modifier = "freq_extreme_but_amt_normal"
        elif z_freq > FREQ_Z_GRAY and amount_signal == "green":
            signal = "gray"
            z_for_delta = z_freq
            freq_modifier = "freq_gray_only"

    delta = _delta_from_z(z_for_delta, signal)
    applied.append(freq_modifier)

    # ───── 사유 반영 ─────
    if reason and signal != "green":
        rc = c_rules._classify_reason(reason)
        if rc in {"경조사", "긴급", "자기투자"}:
            signal = "gray" if signal == "red" else "green"
            delta += BASE_DELTA
            applied.append(f"reason_upgrade:{rc}")

    # 메시지 구성
    parts = [f"[{used}] z_amt={z_amount:.2f}"]
    if freq_signal:
        parts.append(f"이번주 {freq_info['current_week_count']}회 (평소 {freq_info['mu_weekly']:.1f}±{freq_info['sigma_weekly']:.1f}) z_freq={z_freq:.2f}")
    if signal == "red":
        parts.append("★이례")
    reasoning = " · ".join(parts)

    # ───── EMA 업데이트 ─────
    is_anomaly = signal == "red"
    alpha_mu = ALPHA_ANOMALY if is_anomaly else ALPHA
    if cell["count"] >= SEED_MIN_CELL:
        _ema_update(cell, x, alpha_mu)
    _ema_update(glob, x, alpha_mu)
    glob["count"] += 1
    cell["count"] += 1

    # 빈도 events 업데이트 (최근 30일만 유지)
    df["events"].append(exp_date)
    cutoff = (datetime.fromisoformat(exp_date).date() - timedelta(days=FREQ_WINDOW_DAYS + 7)).isoformat()
    df["events"] = [e for e in df["events"] if e >= cutoff]

    save(state)

    return JudgeResponse(
        approach="H",
        signal=signal,
        stat_delta=delta,
        reasoning=reasoning,
        applied_rules=applied,
    )
