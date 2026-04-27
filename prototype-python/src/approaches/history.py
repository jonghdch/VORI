"""세션 메모리 기반 소비 히스토리. 패턴 차원 판정용.

실제 앱에서는 DB로 대체될 예정. 프로토타입에서는 in-memory + 프로세스 생존 동안만 유지.
"""
from __future__ import annotations

from collections import deque
from threading import Lock
from typing import Deque

from models import Expense

MAX_HISTORY = 200
# 생활고정은 패턴 분석에서 제외 (월세·구독료는 반복 당연함)
PATTERN_EXCLUDED_CATEGORIES = {"생활고정"}

_history: Deque[Expense] = deque(maxlen=MAX_HISTORY)
_lock = Lock()


def add(expense: Expense) -> None:
    if expense.category in PATTERN_EXCLUDED_CATEGORIES:
        return
    with _lock:
        _history.append(expense)


def all_items() -> list[Expense]:
    with _lock:
        return list(_history)


def clear() -> None:
    with _lock:
        _history.clear()


def count_description(desc: str) -> int:
    with _lock:
        return sum(1 for e in _history if e.description.strip() == desc.strip())


def count_category(category: str) -> int:
    with _lock:
        return sum(1 for e in _history if e.category == category)


def sum_category_amount(category: str) -> int:
    with _lock:
        return sum(e.amount for e in _history if e.category == category)


def size() -> int:
    with _lock:
        return len(_history)
