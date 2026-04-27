"""이례 지출 후보 풀. 하루 1건 AI 질문의 소스."""
from __future__ import annotations

from threading import Lock

_pool: list[dict] = []
_lock = Lock()
_next_id = 1

MAX_POOL = 50


def add(expense: dict, z: float, priority: float, reasoning: str) -> int:
    global _next_id
    with _lock:
        item = {
            "id": _next_id,
            "expense": expense,
            "z": z,
            "priority": priority,
            "reasoning": reasoning,
            "asked": False,
            "user_reason": None,
            "gemini_verdict": None,
        }
        _next_id += 1
        _pool.append(item)
        _pool.sort(key=lambda x: x["priority"], reverse=True)
        # 가장 낮은 것들 절사
        while len(_pool) > MAX_POOL:
            _pool.pop()
        return item["id"]


def top_unanswered() -> dict | None:
    with _lock:
        for it in _pool:
            if not it["user_reason"]:
                return it
        return None


def get(item_id: int) -> dict | None:
    with _lock:
        for it in _pool:
            if it["id"] == item_id:
                return it
        return None


def answer(item_id: int, user_reason: str, gemini_verdict: dict | None = None) -> dict | None:
    with _lock:
        for it in _pool:
            if it["id"] == item_id:
                it["user_reason"] = user_reason
                it["asked"] = True
                if gemini_verdict:
                    it["gemini_verdict"] = gemini_verdict
                return it
        return None


def all_items() -> list[dict]:
    with _lock:
        return list(_pool)


def clear() -> None:
    global _next_id
    with _lock:
        _pool.clear()
        _next_id = 1
