"""방식 A: Gemini 직접 판정.

LLM에게 지출 정보·사용자 맥락·사유를 넘겨 합리성을 바로 3단계(green/gray/red)로 물음.
특징: 맥락 이해 뛰어남. 단점: 같은 입력에도 답이 변동(비결정론).
결정론이 필요한 경험치 증가 계산은 C가 담당하고, A는 참고·비교용 + 사유 대화용.
"""
from __future__ import annotations

import json
import os
from typing import Optional

from dotenv import load_dotenv

from models import STAT_DELTA_MAP, Expense, JudgeResponse, UserContext

load_dotenv()

MODEL_NAME = os.environ.get("GEMINI_MODEL", "gemini-flash-latest")
_model = None
_init_error: Optional[str] = None


def _init() -> None:
    global _model, _init_error
    if _model is not None or _init_error is not None:
        return
    # GEMINI_API_KEY 또는 GOOGLE_API_KEY 둘 다 허용
    api_key = os.environ.get("GEMINI_API_KEY") or os.environ.get("GOOGLE_API_KEY")
    if not api_key:
        _init_error = "GEMINI_API_KEY / GOOGLE_API_KEY 둘 다 미설정"
        return
    try:
        import google.generativeai as genai

        genai.configure(api_key=api_key)
        _model = genai.GenerativeModel(
            MODEL_NAME,
            system_instruction=(
                "당신은 소비 합리성 판정 AI입니다. "
                "사용자의 재무 프로필·지출·맥락을 보고 해당 지출이 "
                "'합리(green)', '애매(gray)', '비합리(red)' 중 어디에 해당하는지 판정합니다. "
                "반드시 JSON으로만 응답하세요."
            ),
        )
    except Exception as e:  # noqa: BLE001
        _init_error = f"Gemini 초기화 실패: {e}"


PROMPT_TEMPLATE = """[사용자 프로필]
직업: {job}
월수입: {income:,}원
전재산: {assets:,}원
카테고리별 월 예산: {budget}

[지출]
품목: {desc}
금액: {amount:,}원
카테고리: {category}
시간대: {time}
사유: {reason}

[판정 기준]
- green: 예산·필요성·사용자 상황 모두 적합
- gray: 경계선, 이례적이지만 사유가 있으면 이해 가능
- red: 예산 크게 초과, 충동성 강함, 사용자 형편에 과함

[출력: JSON]
{{"signal": "green|gray|red", "reasoning": "한 문장 한국어 근거"}}
"""


def judge(expense: Expense, user_context: UserContext, reason: Optional[str]) -> JudgeResponse:
    _init()
    if _init_error:
        return JudgeResponse(
            approach="A",
            signal="gray",
            stat_delta=0,
            reasoning=f"(스텁) {_init_error}",
            needs_reason=bool(not reason),
        )

    prompt = PROMPT_TEMPLATE.format(
        job=user_context.job,
        income=user_context.monthly_income,
        assets=user_context.total_assets,
        budget=user_context.category_avg,
        desc=expense.description,
        amount=expense.amount,
        category=expense.category,
        time=expense.time_of_day,
        reason=reason or "(없음)",
    )

    try:
        resp = _model.generate_content(
            prompt,
            generation_config={
                "response_mime_type": "application/json",
                "temperature": 0.2,
            },
        )
        text = resp.text.strip()
        data = json.loads(text)
        signal = data.get("signal", "gray")
        if signal not in ("red", "gray", "green"):
            signal = "gray"
        return JudgeResponse(
            approach="A",
            signal=signal,
            stat_delta=STAT_DELTA_MAP[signal],
            reasoning=data.get("reasoning", "") or f"Gemini {MODEL_NAME} 판정",
        )
    except Exception as e:  # noqa: BLE001
        return JudgeResponse(
            approach="A",
            signal="gray",
            stat_delta=0,
            reasoning=f"(Gemini 오류) {str(e)[:120]}",
        )
