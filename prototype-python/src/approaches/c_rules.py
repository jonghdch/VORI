"""방식 C: 규칙 엔진. 화이트박스·결정론적.

수식 설계:
  - 카테고리별 "회당 평균" 기준 (월 평균 / 예상 월간 빈도)
    식비=30회 / 쇼핑뷰티=4회 / 문화여가=3회 / 생활고정=2회
  - 단일 지출 임계 (월수입·전재산·월 예산 대비)
  - 패턴 차원 (반복 소비 감지, 생활고정 제외)
"""
from __future__ import annotations

from typing import Optional

from models import STAT_DELTA_MAP, Expense, JudgeResponse, Signal, UserContext

from . import history

# 카테고리별 월간 예상 소비 빈도. 월 예산을 이 수로 나누면 "회당 평균"
CATEGORY_FREQUENCY = {
    "식비": 30,        # 하루 1회 이상
    "쇼핑뷰티": 4,     # 주 1회 정도
    "문화여가": 3,     # 월 3회 이벤트성
    "생활고정": 2,     # 월세·공과금 등 2회
}


def _classify_reason(reason: Optional[str]) -> Optional[str]:
    if not reason:
        return None
    r = reason.replace(" ", "")
    if any(k in r for k in ["결혼식", "장례", "돌잔치", "축의", "부의", "경조사"]):
        return "경조사"
    if any(k in r for k in ["응급", "사고", "긴급", "병원", "약"]):
        return "긴급"
    if any(k in r for k in ["팀원", "팀장", "회식", "사기", "신임", "회사"]):
        return "사회관계"
    if any(k in r for k in ["학원", "강의", "자격증", "교재", "공부", "자기투자", "자기계발", "독학", "취업준비", "시험준비"]):
        return "자기투자"
    return "기타"


def _signal_to_delta(signal: Signal, upgraded: bool = False) -> int:
    delta = STAT_DELTA_MAP[signal]
    if upgraded and signal != "red":
        delta += 1
    return delta


def _downgrade(signal: Signal) -> Signal:
    if signal == "green":
        return "gray"
    return "red"


def _judge_fixed_cost(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    """생활고정 전용 수식.

    일반 카테고리와 달리 회당·패턴·시간대 룰 적용하지 않음.
    단순 예산 편차율만 평가.
    """
    applied: list[str] = []
    reasoning: list[str] = []
    monthly_avg = user_context.category_avg.get("생활고정", 0) or 1
    ratio = expense.amount / monthly_avg

    if ratio <= 1.0:
        signal: Signal = "green"
        applied.append("fixed_within_budget")
        reasoning.append(f"고정비 · 예산 내 ({ratio * 100:.0f}%)")
    elif ratio <= 1.2:
        signal = "gray"
        applied.append("fixed_over_budget_10_20")
        reasoning.append(f"고정비 · 예산 대비 {ratio * 100:.0f}%")
    else:
        signal = "red"
        applied.append("fixed_over_budget_20plus")
        reasoning.append(f"고정비 · 예산 대비 {ratio * 100:.0f}% (재검토 필요)")

    # 월수입 50% 초과 단일 고정비 → 경고 메시지 (월세가 월수입 초과 등 극단 상황)
    if user_context.monthly_income > 0 and expense.amount / user_context.monthly_income > 0.5:
        applied.append("fixed_over_50pct_of_income")
        reasoning.append("월수입 50% 초과 · 주거/필수 재검토 필요")

    return JudgeResponse(
        approach="C",
        signal=signal,
        stat_delta=_signal_to_delta(signal),
        reasoning=" · ".join(reasoning),
        applied_rules=applied,
    )


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    # ───────────── 생활고정 별도 수식 ─────────────
    if expense.category == "생활고정":
        return _judge_fixed_cost(expense, user_context, reason)

    # ───────────── 유해 키워드 오버라이드 (금액 무관 red) ─────────────
    HARMFUL = ["도박", "배팅", "토토", "사다리", "카지노", "유흥주점", "룸살롱"]
    desc_norm = expense.description.replace(" ", "")
    if any(k in desc_norm for k in HARMFUL):
        return JudgeResponse(
            approach="C",
            signal="red",
            stat_delta=_signal_to_delta("red"),
            reasoning=f"유해 소비 키워드 매칭 · 금액 무관 red",
            applied_rules=["harmful_keyword_override"],
        )

    applied: list[str] = []
    reasoning_parts: list[str] = []

    monthly_avg = user_context.category_avg.get(expense.category, 0) or 1
    freq = CATEGORY_FREQUENCY.get(expense.category, 10)
    per_txn_avg = max(monthly_avg / freq, 1.0)
    per_txn_ratio = expense.amount / per_txn_avg
    share_of_monthly = expense.amount / monthly_avg

    reason_cat = _classify_reason(reason)
    reason_upgrade_eligible = reason_cat in {"경조사", "긴급", "자기투자"}

    signal: Signal = "green"

    # ───────────── 회당 평균 기준 ─────────────
    # Rule 2: 회당 평균 대비 8배 초과 → red (이전 5배에서 완화, 이벤트성 소비 여지)
    if per_txn_ratio > 8.0:
        signal = "red"
        applied.append("per_txn_over_8x")
        reasoning_parts.append(f"회당 평균 대비 {per_txn_ratio:.1f}배 초과")
    # Rule 3: 회당 평균 대비 1.5배 초과 → gray
    elif per_txn_ratio > 1.5:
        signal = "gray"
        applied.append("per_txn_over_1_5x")
        reasoning_parts.append(f"회당 평균 대비 {per_txn_ratio:.1f}배")
    else:
        applied.append("per_txn_within_avg")
        reasoning_parts.append(f"회당 평균 대비 {per_txn_ratio:.2f}배")

    # (단일 지출 × 월 예산 비중 룰은 per_txn_ratio 와 중복되어 제거)

    # ───────────── 시간대 ─────────────
    # Rule 6: 새벽 시간대 식비·쇼핑 → 한 단계 하향
    if expense.time_of_day == "새벽" and expense.category in {"식비", "쇼핑뷰티"}:
        signal = _downgrade(signal)
        applied.append("late_night_impulse_downgrade")
        reasoning_parts.append("새벽 시간대 충동성 가능")

    # ───────────── 패턴 차원 (생활고정 제외) ─────────────
    if expense.category != "생활고정":
        repeat = history.count_description(expense.description)
        cat_cum = history.sum_category_amount(expense.category)
        projected = (cat_cum + expense.amount) / monthly_avg if monthly_avg > 0 else 0.0

        # 이번 지출의 크기 분류 (회당 평균 대비)
        # minimal(≤0.5배): 절약형 / normal(≤1.5배): 보통 / large(>1.5배): 큼
        if per_txn_ratio <= 0.5:
            sizing = "minimal"
        elif per_txn_ratio <= 1.5:
            sizing = "normal"
        else:
            sizing = "large"

        # Rule 7: 같은 description 5회 이상 반복 → 최소 gray
        if repeat >= 5 and signal == "green":
            signal = "gray"
            applied.append("pattern_repeat_5x")
            reasoning_parts.append(f"같은 지출 최근 {repeat + 1}회째")

        # Rule 8: 같은 description 10회 이상 → red
        if repeat >= 10:
            signal = "red"
            applied.append("pattern_repeat_10x")
            reasoning_parts.append(f"같은 지출 {repeat + 1}회째 · 습관성")

        # Rule 9 ~ 11: 카테고리 누적 + 지출 크기 복합
        # 중요: 이전 누적이 있을 때만 적용. 첫 지출이면 per_txn_ratio 로 충분.
        if cat_cum > 0:
            if projected > 1.0:  # 월 예산 초과
                if sizing == "large":
                    signal = "red"
                    applied.append("budget_over_large")
                    reasoning_parts.append(f"{expense.category} 예산 초과 + 큰 지출")
                elif sizing == "normal":
                    if signal == "green":
                        signal = "gray"
                    applied.append("budget_over_normal")
                    reasoning_parts.append(f"{expense.category} 예산 초과 · 경고")
                else:  # minimal
                    applied.append("budget_over_minimal_ok")
                    reasoning_parts.append(f"{expense.category} 예산 초과 중이나 절약형 선택 인정")
            elif projected >= 0.80:
                if sizing == "large":
                    signal = _downgrade(signal)
                    applied.append("budget_warn_80_large")
                    reasoning_parts.append(f"{expense.category} 누적 {projected*100:.0f}% + 큰 지출")
                elif sizing == "normal":
                    if signal == "green":
                        signal = "gray"
                    applied.append("budget_warn_80_normal")
                    reasoning_parts.append(f"{expense.category} 누적 {projected*100:.0f}%")

    # ───────────── 사유 기반 상향 (최대 1단계, red→green 점프 금지) ─────────────
    upgraded = False
    if reason_upgrade_eligible and signal != "green":
        signal = "gray" if signal == "red" else "green"
        upgraded = True
        applied.append(f"reason_upgrade:{reason_cat}")
        reasoning_parts.append(f"사유 '{reason_cat}' 인정 · 1단계 상향")

    # ───────────── 안전장치 (모든 상향 덮어쓰기) ─────────────
    # Rule 11: 전재산 10% 초과 단일 지출 → red 고정
    if user_context.total_assets > 0 and expense.amount / user_context.total_assets > 0.10:
        signal = "red"
        applied.append("asset_over_10pct")
        reasoning_parts.append("전재산 10% 초과 단일 지출")
        upgraded = False

    # Rule 12: 월수입 30% 초과 단일 지출 → red 고정
    if user_context.monthly_income > 0 and expense.amount / user_context.monthly_income > 0.30:
        signal = "red"
        applied.append("income_over_30pct")
        reasoning_parts.append("월수입 30% 초과 단일 지출")
        upgraded = False
    # Rule 13: 월수입 15% 초과 단일 지출 → 최소 gray
    elif user_context.monthly_income > 0 and expense.amount / user_context.monthly_income > 0.15:
        if signal == "green":
            signal = "gray"
            applied.append("income_over_15pct")
            reasoning_parts.append("월수입 15% 초과 단일 지출")

    return JudgeResponse(
        approach="C",
        signal=signal,
        stat_delta=_signal_to_delta(signal, upgraded=upgraded),
        reasoning=" · ".join(reasoning_parts),
        applied_rules=applied,
    )
