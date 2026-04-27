"""Vori 판정 방식 비교 프로토타입 서버."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from approaches import (
    a_gemini,
    anomaly_pool,
    b_knn,
    c_rules,
    d_hybrid,
    e_adaptive,
    f_robust,
    g_context,
    h_freq,
    i_integrated,
    j_simplified,
    history as history_store,
    state_store,
)
from models import Approach, Expense, JudgeResponse, UserContext

ROOT = Path(__file__).parent
SAMPLES_PATH = ROOT / "test_samples.json"


class JudgeRequest(BaseModel):
    approach: Approach
    expense: Expense
    user_context: UserContext
    reason: Optional[str] = None


class JudgeAllRequest(BaseModel):
    expense: Expense
    user_context: UserContext
    reason: Optional[str] = None


app = FastAPI(title="Vori Judgment Prototype", version="0.1.0")
app.mount("/static", StaticFiles(directory=str(ROOT / "static")), name="static")


@app.on_event("startup")
def _startup() -> None:
    # B의 임베딩 모델을 미리 로드·워밍업해서 첫 사용자 요청 지연 제거
    b_knn.preload()


APPROACH_MAP = {
    "A": a_gemini.judge,
    "B": b_knn.judge,
    "C": c_rules.judge,
    "D": d_hybrid.judge,
    "E": e_adaptive.judge,
    "F": f_robust.judge,
    "G": g_context.judge,
    "H": h_freq.judge,
    "I": i_integrated.judge,
    "J": j_simplified.judge,
}


@app.get("/")
def root() -> FileResponse:
    return FileResponse(str(ROOT / "static" / "index.html"))


@app.get("/samples")
def samples() -> list[dict]:
    with SAMPLES_PATH.open(encoding="utf-8") as f:
        return json.load(f)


@app.post("/judge", response_model=JudgeResponse)
def judge(req: JudgeRequest) -> JudgeResponse:
    fn = APPROACH_MAP.get(req.approach)
    if fn is None:
        raise HTTPException(400, f"unknown approach: {req.approach}")
    return fn(req.expense, req.user_context, req.reason)


@app.post("/judge-all")
def judge_all(req: JudgeAllRequest) -> list[JudgeResponse]:
    # 9방식 전체 비교
    results = []
    for key in ("A", "B", "C", "D", "E", "F", "G", "H", "I"):
        fn = APPROACH_MAP[key]
        results.append(fn(req.expense, req.user_context, req.reason))
    # 패턴 차원 판정을 위해 히스토리에 적재 (생활고정은 내부에서 자동 제외)
    history_store.add(req.expense)
    return results


@app.get("/history")
def history_get() -> dict:
    items = history_store.all_items()
    return {"count": len(items), "items": [e.model_dump() for e in items]}


@app.delete("/history")
def history_clear() -> dict:
    history_store.clear()
    return {"cleared": True}


# ───────── 방식 E (적응형) 상태 관리 ─────────

@app.get("/adaptive/state")
def adaptive_state() -> dict:
    return state_store.load()


@app.delete("/adaptive/state")
def adaptive_reset() -> dict:
    state_store.reset()
    f_robust.reset()
    g_context.reset()
    h_freq.reset()
    i_integrated.reset()
    j_simplified.reset()
    anomaly_pool.clear()
    return {"reset": True}


# ───────── 이례 질문 풀 ─────────

@app.get("/anomaly/today")
def anomaly_today() -> dict:
    item = anomaly_pool.top_unanswered()
    if not item:
        return {"has_question": False}
    return {"has_question": True, "item": item}


@app.get("/anomaly/all")
def anomaly_all() -> list[dict]:
    return anomaly_pool.all_items()


class AnswerRequest(BaseModel):
    item_id: int
    reason: str
    user_context: UserContext


@app.post("/anomaly/answer")
def anomaly_answer(req: AnswerRequest) -> dict:
    item = anomaly_pool.get(req.item_id)
    if not item:
        raise HTTPException(404, "item not found")
    # Gemini로 사유 반영 재판정 (A 재사용)
    expense = Expense(**item["expense"])
    gemini_res = a_gemini.judge(expense, req.user_context, req.reason).model_dump()
    updated = anomaly_pool.answer(req.item_id, req.reason, gemini_res)
    # 원래 감점과 Gemini 재판정 신호에 따라 보정 제안
    signal_order = {"red": 0, "gray": 1, "green": 2}
    original_signal = "red"  # E는 이례 = red 처리했으므로
    new_signal = gemini_res["signal"]
    step_diff = signal_order[new_signal] - signal_order[original_signal]
    # 한 단계 상향까지만 인정 (점프 금지)
    step_diff = max(-1, min(1, step_diff))
    adjustment = step_diff * 3  # 1단계 상향 시 +3점 보정
    return {
        "item": updated,
        "gemini": gemini_res,
        "stat_adjustment": adjustment,
    }
