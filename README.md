# Vori 판정 프로토타입

Vori 프로젝트의 "합리성 판정" 백엔드 방식 3개를 같은 API로 나란히 비교하기 위한 로컬 프로토타입.

## 방식

| 키 | 방식 | 설명 |
|---|---|---|
| A | Gemini 직접 판정 | 스텁. API 키 발급 후 활성화 |
| B | 임베딩 + k-NN | `jhgan/ko-sroberta-multitask` 로컬 모델 + 라벨 DB 22건 최근접 이웃 다수결 |
| C | 규칙 엔진 | 명시적 룰 8개 (시간대·카테고리·금액비·사유) |
| D | 하이브리드 | C 확정 룰 우선 → 애매하면 B로, 심각 불일치 시 보수 선택 |

## 실행

```bash
cd ~/Projects/vori-backend-prototype
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

첫 실행 시 `sentence-transformers` 모델이 다운로드됩니다 (~400MB).

- 테스트 UI: http://localhost:8000
- API 문서: http://localhost:8000/docs

## API

- `POST /judge` — 단일 방식 판정 (body: `approach` + 지출 + 맥락 + 사유)
- `POST /judge-all` — B/C/D 세 방식 동시 실행
- `GET /samples` — 미리 준비된 테스트 케이스 10건
- `GET /` — 테스트 UI

## 결과 포맷

```json
{
  "approach": "D",
  "signal": "gray",
  "stat_delta": 1,
  "needs_reason": false,
  "reasoning": "...",
  "similar_cases": [{"description": "...", "label": "gray", "similarity": 0.72}],
  "applied_rules": ["amount_within_avg"]
}
```

## 스탯 가감 값 (초안)

- 🟢 green: +3
- ⚪ gray : +1
- 🔴 red : -2
- 사유 기반 1단계 상향 성공 시 +1 보정

## 라벨 DB (B용)

`data/labeled_examples.json` — 22건. 카테고리·시간대·금액비(평균 대비)·레이블 포함.
부족하면 여기를 늘려서 k-NN 품질 개선 가능.

## 다음 단계

- Gemini API 키 발급 → `approaches/a_gemini.py` 실제 구현
- 라벨 DB 100건 이상으로 확장
- 규칙 개수·임계값 튜닝 (`approaches/c_rules.py`)
- 하이브리드 가중치 조정 (`AMBIGUITY_THRESHOLD`, `DECISIVE_C_RULES`)
