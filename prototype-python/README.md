# Python Prototype (참고용)

> **이 폴더는 본 빌드(Java + Spring Boot)용 참조 자산입니다. 운영 X.**

졸작 본 빌드는 Java + Spring Boot 로 진행되고, 본 폴더는 다음 용도로만 보존:

- 4 방식 (A Gemini / B 임베딩 kNN / C 룰엔진 / D 하이브리드) 알고리즘 로직 참조
- 라벨 DB (`src/data/labeled_examples.json`, 22건) — Java 에서 그대로 사용 가능 (JSON, 언어 무관)
- 룰엔진 8개 정의 (`src/approaches/c_rules.py`) — Java 로 변환 시 참조

## 구조 (이동 전 상태 그대로)

```
prototype-python/
├── src/               ← Python FastAPI 프로토타입
│   ├── main.py
│   ├── models.py
│   ├── personas.py
│   ├── approaches/    ← 4 방식 + 확장 e~j (총 12 파일)
│   ├── data/          ← labeled_examples.json (Java 에서 재사용)
│   ├── state/         ← 적응형 학습 상태 (e~j)
│   └── static/index.html
├── tools/             ← 비교·시뮬레이션 스크립트
├── tests/             ← pytest
└── requirements.txt
```

## 실행 (참고용)

본 빌드 진행 중엔 실행 안 함. 디버그 시:

```bash
cd prototype-python
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn src.main:app --reload --port 8000
```

## Java 재작성 매핑

| Python 파일 | Java/Spring 등가 |
|------------|----------------|
| `src/main.py` (FastAPI) | `BackendApplication.java` (Spring Boot) |
| `src/models.py` (Pydantic) | `dto/JudgeRequest.java`, `dto/JudgeResponse.java` |
| `src/approaches/a_gemini.py` | `service/GeminiJudgeService.java` |
| `src/approaches/b_knn.py` | `service/KnnJudgeService.java` (DJL or Python 마이크로서비스) |
| `src/approaches/c_rules.py` | `service/RuleJudgeService.java` |
| `src/approaches/d_hybrid.py` | `service/HybridJudgeService.java` |
| `src/data/labeled_examples.json` | 그대로 사용, `resources/labeled_examples.json` |
