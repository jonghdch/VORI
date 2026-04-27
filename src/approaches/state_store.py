"""방식 E의 영속 상태 저장.

카테고리별 개인 평균·편차·누적 건수를 JSON으로 보관.
서버 재시작해도 유지되어야 실제 "적응" 효과를 시연할 수 있음.
"""
from __future__ import annotations

import json
from pathlib import Path
from threading import Lock

STATE_PATH = Path(__file__).parent.parent / "state" / "adaptive_state.json"
CATEGORIES = ["식비", "쇼핑뷰티", "문화여가", "생활고정"]
SEED_MIN = 3  # 카테고리별 이 개수 쌓이면 판정 시작 (튜토리얼 종료 기준)

_lock = Lock()


def _empty_state() -> dict:
    return {
        "categories": {
            c: {"mu": 0.0, "sigma": 0.0, "count": 0, "seeds": []}
            for c in CATEGORIES
        },
        "total_count": 0,
    }


def load() -> dict:
    with _lock:
        if not STATE_PATH.exists():
            STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
            s = _empty_state()
            STATE_PATH.write_text(json.dumps(s, ensure_ascii=False, indent=2))
            return s
        return json.loads(STATE_PATH.read_text(encoding="utf-8"))


def save(state: dict) -> None:
    with _lock:
        STATE_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def reset() -> None:
    save(_empty_state())


def is_category_seeded(state: dict, category: str) -> bool:
    c = state["categories"].get(category)
    return bool(c and c["count"] >= SEED_MIN)
