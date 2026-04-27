"""공통 Pydantic 모델."""
from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field

Signal = Literal["red", "gray", "green"]
TimeOfDay = Literal["새벽", "아침", "점심", "저녁", "밤"]
Category = Literal["식비", "쇼핑뷰티", "문화여가", "생활고정"]
Approach = Literal["A", "B", "C", "D", "E", "F", "G", "H", "I", "J"]


class Expense(BaseModel):
    description: str
    amount: int
    category: Category
    time_of_day: TimeOfDay
    date: Optional[str] = None  # ISO 8601 (YYYY-MM-DD). 미지정 시 서버 today.


class UserContext(BaseModel):
    job: str
    monthly_income: int
    total_assets: int
    category_avg: dict[str, int]


class SimilarCase(BaseModel):
    description: str
    label: Signal
    similarity: float


class JudgeResponse(BaseModel):
    approach: Approach
    signal: Signal
    stat_delta: int
    needs_reason: bool = False
    reasoning: str
    similar_cases: list[SimilarCase] = Field(default_factory=list)
    applied_rules: list[str] = Field(default_factory=list)


STAT_DELTA_MAP = {"green": 3, "gray": 1, "red": -2}
