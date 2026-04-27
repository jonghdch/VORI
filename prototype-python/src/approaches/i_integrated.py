"""방식 I: 통합 적응 모델 (Integrated Adaptive).

H에서 식별된 단점을 모두 흡수:
  1. **시간 감쇠** — calendar time 기반 EMA α (오래 안 본 셀은 빠른 적응)
  2. **베이지안 사전분포** — cold-start 셀·새 description 모두 prior 사용
  3. **사용자 목표** — UserContext.category_avg = 월 한도 목표
  4. **카테고리 예산 풀** — 월 누적 vs 일자비례 진행률 추적, 페이스 위반 경고
  5. **확신도 (Confidence)** — 학습 단계 (count) 따라 판정 강도 조절
  6. **빈도 베이지안 prior** — 새 description도 즉시 빈도 분석 (가상 1회/주 시작)

H 기능 유지:
  - (cat × tod × weekday/weekend) 셀
  - 빈도 z-score
  - 5/10 hardcode 룰 없음
  - 생활고정 → C 위임
  - 사유 1단계 상향
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

STATE_PATH = Path(__file__).parent.parent / "state" / "i_state.json"

# 파라미터 ─────────────────
TAU_DAYS = 30                 # 시간 감쇠 반감기 (며칠 안 보면 더 빠른 적응)
PRIOR_N = 5                   # 베이지안 prior 가상 샘플 수
PRIOR_FREQ_WEEKLY = 1.0       # 새 desc 기본 주간 빈도 prior
PRIOR_FREQ_SIGMA = 1.5
SEED_MIN_GLOBAL = 5
Z_AMT_THRESHOLD = 2.0
Z_FREQ_GRAY = 2.0
Z_FREQ_RED = 3.0
BASE_DELTA = 3
CONFIDENCE_FULL = 50          # count 50 이후 full confidence
PACE_WARN = 0.50              # 월예산 진행률 +50%p 빠르면 경고 (실 데이터 고려)
PACE_CRIT = 1.00              # +100%p (예산 2배 페이스) 면 강한 경고

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
        sub["_monthly"] = {}
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


def _days_between(d_str_a: Optional[str], d_str_b: str) -> int:
    if not d_str_a:
        return 0
    try:
        a = datetime.fromisoformat(d_str_a).date()
        b = datetime.fromisoformat(d_str_b).date()
        return abs((b - a).days)
    except Exception:
        return 0


def _time_decayed_alpha(days_since: int) -> float:
    """시간 감쇠 α — 오래 안 본 셀일수록 큰 α (빠른 적응)."""
    base = 0.15
    extra = (1 - math.exp(-days_since / TAU_DAYS)) * 0.30
    return min(0.50, base + extra)


def _ema_update(cell: dict, x: float, alpha_mu: float) -> None:
    mu = cell["mu"]
    sigma = cell["sigma"] if cell["sigma"] > 0 else max(mu * 0.3, 1.0)
    new_mu = alpha_mu * x + (1 - alpha_mu) * mu
    dev_cap = max(3.0 * sigma, mu * 0.5, 1.0)
    dev_b = max(-dev_cap, min(dev_cap, x - mu))
    new_sigma = math.sqrt(0.15 * dev_b ** 2 + 0.85 * sigma ** 2)
    cell["mu"] = new_mu
    cell["sigma"] = new_sigma


def _bayesian_z(x: float, cell: dict, prior_mu: float, prior_sigma: float) -> tuple[float, float, float]:
    """베이지안 posterior z-score. n_obs와 prior_n의 가중평균."""
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


def _confidence(count: int) -> float:
    """학습 단계별 확신도 0.5 ~ 1.0."""
    return 0.5 + 0.5 * min(1.0, count / CONFIDENCE_FULL)


def _compute_freq_z_bayesian(events: list[str], current_date: str) -> tuple[float, dict]:
    """빈도 z-score (베이지안 prior 반영)."""
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
        # cold-start: prior로 보강
        n = len(recent)
        post_mu = (n * statistics.mean(weeks) + PRIOR_N * PRIOR_FREQ_WEEKLY) / (n + PRIOR_N) if n > 0 else PRIOR_FREQ_WEEKLY
        post_sigma = PRIOR_FREQ_SIGMA
    else:
        mu_w = statistics.mean(weeks)
        sig_w = statistics.stdev(weeks) if len(set(weeks)) > 1 else max(mu_w * 0.3, 0.5)
        sig_w = max(sig_w, 0.5)
        n = len(recent)
        # 데이터 많으면 prior 영향 감소
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
            approach="I", signal=c_res.signal, stat_delta=c_res.stat_delta,
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
        sub["_monthly"] = {}
        state["categories"][cat] = sub
    sub = state["categories"][cat]
    cell = sub.setdefault(cell_key, _empty_cell())
    glob = sub.setdefault("_global", _empty_cell())
    monthly = sub.setdefault("_monthly", {})
    desc_freq = state.setdefault("desc_freq", {})
    df = desc_freq.setdefault(expense.description, {"events": []})

    monthly_goal = user_context.category_avg.get(cat, 0)
    applied: list[str] = [f"cell:{cat}/{cell_key}"]
    x = expense.amount

    # ───── 시드 단계 (글로벌 5건까지만) ─────
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
            msg = f"카테고리 시드 완료 (μ={mu:,.0f})"
            applied.append("global_seeded")
        else:
            msg = f"시드 수집 중 ({glob['count']}/{SEED_MIN_GLOBAL})"
            applied.append("collecting")
        df["events"].append(exp_date)
        save(state)
        return JudgeResponse(approach="I", signal="gray", stat_delta=0, reasoning=msg, applied_rules=applied)

    # ───── 베이지안 z-score (셀 + 글로벌 prior) ─────
    prior_mu = glob["mu"]
    prior_sigma = glob["sigma"] if glob["sigma"] > 0 else max(glob["mu"] * 0.3, 1.0)
    z_amount, post_mu, post_sigma = _bayesian_z(x, cell, prior_mu, prior_sigma)
    amount_signal = _signal_from_z(z_amount)
    applied.append(f"z_amt={z_amount:.2f}")

    # ───── 빈도 z-score (베이지안 prior 반영) ─────
    z_freq, freq_info = _compute_freq_z_bayesian(df["events"], exp_date)
    freq_signal = _signal_from_z(z_freq, threshold_red=Z_FREQ_RED) if freq_info["events_count"] >= 1 else None

    # ───── 종합 (H 로직) ─────
    signal = amount_signal
    z_for_delta = z_amount
    if freq_signal:
        if z_freq > Z_FREQ_RED:
            if amount_signal != "green":
                signal = "red"
                z_for_delta = max(z_amount, z_freq)
                applied.append("freq_extreme_red")
            else:
                signal = "gray"
                z_for_delta = z_freq
                applied.append("freq_extreme_gray")
        elif z_freq > Z_FREQ_GRAY and amount_signal == "green":
            signal = "gray"
            z_for_delta = z_freq
            applied.append("freq_gray")

    # ───── 카테고리 예산 풀: 월 진행률 (정보용 메타, 판정에는 약하게만 반영) ─────
    # 사용자가 입력한 category_avg가 진짜 "지키고 싶은 목표"가 아닐 수 있어
    # 보수적 적용: 예산 200% 초과 페이스만 약한 경고
    month_key = exp_date[:7]
    mstate = monthly.setdefault(month_key, {"spent": 0, "count": 0})
    cum_after = mstate["spent"] + x
    if monthly_goal > 0:
        cur_d_obj = datetime.fromisoformat(exp_date).date()
        day_of_month = cur_d_obj.day
        progress = cum_after / monthly_goal
        expected = day_of_month / 30.0
        # 정말 극단적 페이스 (예산 200%+ 초과 페이스) 일 때만 경고
        if progress > expected + 1.5 and signal == "green":
            signal = "gray"
            applied.append(f"pace_extreme({progress*100:.0f}%>{expected*100:.0f}%)")

    # ───── delta + 확신도 가중 ─────
    if signal == "green":
        delta = BASE_DELTA if z_amount <= 0 else max(BASE_DELTA // 2, 1)
    elif signal == "gray":
        delta = -max(BASE_DELTA // 2, 1)
    else:
        delta = -min(int(BASE_DELTA + z_for_delta * 1.5), 12)

    conf = _confidence(glob["count"])
    delta = int(delta * (0.5 + 0.5 * conf))
    applied.append(f"conf={conf:.2f}")

    # ───── 사유 ─────
    if reason and signal != "green":
        rc = c_rules._classify_reason(reason)
        if rc in {"경조사", "긴급", "자기투자"}:
            signal = "gray" if signal == "red" else "green"
            delta += BASE_DELTA
            applied.append(f"reason:{rc}")

    parts = [f"z_amt={z_amount:.2f} (μ={post_mu:,.0f}±{post_sigma:,.0f})"]
    if freq_signal:
        parts.append(f"이번주 {freq_info['current_week']}회 (post={freq_info['post_mu']:.1f}) z_freq={z_freq:.2f}")
    if monthly_goal > 0:
        parts.append(f"월예산 {progress*100:.0f}%/{expected*100:.0f}%")
    if signal == "red":
        parts.append("★이례")
    reasoning = " · ".join(parts)

    # ───── 시간 감쇠 EMA 업데이트 ─────
    days_since = _days_between(cell["last_update_date"], exp_date)
    alpha = _time_decayed_alpha(days_since)
    if signal == "red":
        alpha = min(alpha, 0.05)  # 이례 시 약하게 (E와 동일 정책)
    _ema_update(cell, x, alpha)
    _ema_update(glob, x, alpha)
    cell["last_update_date"] = exp_date
    glob["last_update_date"] = exp_date
    cell["count"] += 1
    glob["count"] += 1

    # 월별 누적
    mstate["spent"] = cum_after
    mstate["count"] += 1

    # 빈도 events
    df["events"].append(exp_date)
    cutoff = (datetime.fromisoformat(exp_date).date() - timedelta(days=35)).isoformat()
    df["events"] = [e for e in df["events"] if e >= cutoff]

    save(state)

    return JudgeResponse(approach="I", signal=signal, stat_delta=delta, reasoning=reasoning, applied_rules=applied)
