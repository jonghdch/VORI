"""방식 B: 임베딩 + k-NN. 라벨 DB의 TOP-K 다수결."""
from __future__ import annotations

import json
from collections import Counter, OrderedDict
from pathlib import Path
from typing import Optional

import numpy as np
from sentence_transformers import SentenceTransformer

from models import STAT_DELTA_MAP, Expense, JudgeResponse, SimilarCase, Signal, UserContext

MODEL_NAME = "jhgan/ko-sroberta-multitask"
LABELS_PATH = Path(__file__).parent.parent / "data" / "labeled_examples.json"
TOP_K = 5
QUERY_CACHE_MAX = 256

_model: Optional[SentenceTransformer] = None
_examples: list[dict] = []
_example_vecs: Optional[np.ndarray] = None
_query_cache: "OrderedDict[str, np.ndarray]" = OrderedDict()


def preload() -> None:
    """서버 startup에서 호출. 첫 요청 지연을 사전에 소화."""
    _load()
    # 워밍업 encode로 토치 초기화 지연도 흡수
    assert _model is not None
    _model.encode(["워밍업"], normalize_embeddings=True)


def _load() -> None:
    global _model, _examples, _example_vecs
    if _model is not None:
        return
    _model = SentenceTransformer(MODEL_NAME)
    with LABELS_PATH.open(encoding="utf-8") as f:
        _examples = json.load(f)
    texts = [_build_text(e) for e in _examples]
    _example_vecs = _model.encode(texts, normalize_embeddings=True)


def _encode_query(text: str) -> np.ndarray:
    """같은 텍스트 재요청 시 재사용. /judge-all 에서 B와 D가 중복 encode 하는 걸 방지."""
    if text in _query_cache:
        _query_cache.move_to_end(text)
        return _query_cache[text]
    assert _model is not None
    vec = _model.encode([text], normalize_embeddings=True)[0]
    _query_cache[text] = vec
    if len(_query_cache) > QUERY_CACHE_MAX:
        _query_cache.popitem(last=False)
    return vec


def _build_text(item: dict) -> str:
    return f"[{item['category']}][{item['time_of_day']}] {item['description']}"


def _build_query_text(expense: Expense, user_context: UserContext) -> str:
    avg = user_context.category_avg.get(expense.category, 0) or 1
    ratio = expense.amount / avg
    return (
        f"[{expense.category}][{expense.time_of_day}] {expense.description} "
        f"{expense.amount}원 (평균 대비 {ratio:.1f}배)"
    )


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    _load()
    assert _model is not None and _example_vecs is not None

    query_text = _build_query_text(expense, user_context)
    if reason:
        query_text += f" | 사유: {reason}"
    query_vec = _encode_query(query_text)

    sims = _example_vecs @ query_vec
    top_idx = np.argsort(sims)[::-1][:TOP_K]

    top_cases: list[SimilarCase] = []
    # 유사도 가중 다수결: 각 라벨에 대해 유사도 합산
    label_scores: dict[Signal, float] = {"red": 0.0, "gray": 0.0, "green": 0.0}
    label_counts: Counter = Counter()
    for i in top_idx:
        ex = _examples[int(i)]
        sim = float(sims[int(i)])
        top_cases.append(
            SimilarCase(description=ex["description"], label=ex["label"], similarity=sim)
        )
        label_scores[ex["label"]] += max(sim, 0.0)
        label_counts[ex["label"]] += 1

    # 최대 점수 라벨. 동률이면 gray(중립) 우선 — "확실하지 않음 = 애매"
    tie_order = {"gray": 0, "red": 1, "green": 2}
    majority: Signal = max(
        label_scores.items(),
        key=lambda kv: (kv[1], -tie_order[kv[0]]),
    )[0]

    # 사유가 정당 카테고리(경조사/긴급/자기투자) 키워드 포함 시 1단계 상향
    upgraded = False
    if reason and any(k in reason for k in ["경조사", "결혼식", "장례", "긴급", "응급", "병원", "학원", "강의", "자격증", "자기투자", "자기계발"]):
        if majority == "red":
            majority = "gray"
            upgraded = True
        elif majority == "gray":
            majority = "green"
            upgraded = True

    # 안전장치 (C 룰과 동일): 생활고정은 별도 수식이므로 스킵
    applied_safety: list[str] = []
    if expense.category != "생활고정":
        if user_context.total_assets > 0 and expense.amount / user_context.total_assets > 0.10:
            majority = "red"
            applied_safety.append("asset_over_10pct")
            upgraded = False
        if user_context.monthly_income > 0 and expense.amount / user_context.monthly_income > 0.30:
            majority = "red"
            applied_safety.append("income_over_30pct")
            upgraded = False
        elif user_context.monthly_income > 0 and expense.amount / user_context.monthly_income > 0.15:
            if majority == "green":
                majority = "gray"
                applied_safety.append("income_over_15pct")

    reasoning = (
        f"TOP-{TOP_K} 가중 다수결 "
        f"({label_counts['green']}🟢/{label_counts['gray']}⚪/{label_counts['red']}🔴, "
        f"점수 🟢{label_scores['green']:.2f}·⚪{label_scores['gray']:.2f}·🔴{label_scores['red']:.2f})"
    )
    if upgraded:
        reasoning += " · 사유 반영 1단계 상향"
    if applied_safety:
        reasoning += f" · 안전장치({','.join(applied_safety)})"

    delta = STAT_DELTA_MAP[majority]
    if upgraded and majority != "red":
        delta += 1

    return JudgeResponse(
        approach="B",
        signal=majority,
        stat_delta=delta,
        reasoning=reasoning,
        similar_cases=top_cases,
    )
