"""방식 D: 하이브리드.

[현재] B(임베딩) 제거 결정 후 D는 C 규칙 엔진과 동일한 판정을 반환.
[향후] Gemini API 키 발급 후, D는 다음과 같이 확장됨:
  1. C 규칙으로 판정
  2. 이례 지출이면 A(Gemini)가 사용자에게 자연어로 사유 질문
  3. 받은 사유를 A가 카테고리 분류·맥락 이해
  4. 최종 판정 (판정 자체는 C 룰 기반으로 결정론 유지)
"""
from __future__ import annotations

from typing import Optional

from models import Expense, JudgeResponse, UserContext

from . import c_rules


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    c_result = c_rules.judge(expense, user_context, reason)
    return JudgeResponse(
        approach="D",
        signal=c_result.signal,
        stat_delta=c_result.stat_delta,
        needs_reason=c_result.needs_reason,
        reasoning=f"[C 규칙 기반] {c_result.reasoning}",
        similar_cases=c_result.similar_cases,
        applied_rules=c_result.applied_rules,
    )
