# Vori — Project Memory

> 합리성 판정 백엔드. 지출+맥락+사유를 받아 4 방식(Gemini / 임베딩 kNN / 룰엔진 / 하이브리드)으로 green/gray/red 시그널 + 스탯 가감 반환. 졸업작품용 정식 빌드 진행 예정.

## 빠른 참조

- **상태**: 백엔드 프로토타입 → 정식 빌드 시작 대기 (졸작 일정 기준)
- **타겟**: 시연 20명 (졸작 시연 기준), 친구·지인 100명 확장 가능
- **스택**: Python FastAPI (백) + (예정) Next.js+TS PWA (웹) → React Native (모바일 Phase 2)
- **핵심 ML 자산**: `jhgan/ko-sroberta-multitask` 한국어 임베딩 (B 방식 kNN)

## 디렉토리 규약

```
~/Projects/vori/
├── CLAUDE.md           ← 이 파일
├── README.md           ← 프로토타입 설명 (방식 A/B/C/D 비교)
├── requirements.txt
├── src/                ← FastAPI 백엔드
│   ├── main.py         ← 엔트리, 라우터
│   ├── models.py       ← Pydantic 스키마
│   ├── personas.py     ← 라벨 페르소나 정의
│   ├── approaches/     ← 4 방식 + 확장 (a~j)
│   │   ├── a_gemini.py     (방식 A: Gemini 직접)
│   │   ├── b_knn.py        (방식 B: 임베딩 + kNN)
│   │   ├── c_rules.py      (방식 C: 규칙 엔진 8개)
│   │   ├── d_hybrid.py     (방식 D: 하이브리드)
│   │   └── e~j_*.py        (확장 실험 — adaptive/robust/context/freq/integrated/simplified)
│   ├── data/labeled_examples.json   ← B 방식 kNN 라벨 DB (22건)
│   ├── state/                       ← 적응형 상태 (e~j 방식)
│   └── static/index.html            ← 테스트 UI
├── tests/              ← pytest
│   ├── test_e_analysis.py
│   ├── test_suite.py
│   └── test_samples.json
├── tools/              ← 비교·시뮬레이션 스크립트 (정기 운영용 X, 분석용)
│   ├── compare_ef.py / compare_efg.py / compare_egh.py / compare_eghi.py / compare_ghij.py / compare_z.py
│   ├── simulation_6m.py / simulation_deep.py / simulation_multi.py
│   └── analyze_deep.py
├── docs/               ← 결정 기록·아키텍처 (현재 비어있음, 정식 빌드 시 ADR 추가)
├── skills/             ← AI 워크플로 (정식 빌드 시 신설 — 판정·라벨링·평가)
├── hooks/              ← 가드레일 (정식 빌드 시 신설)
├── results/            ← 시뮬레이션·비교 출력 (.json/.log/.out — gitignore 대상)
└── venv/               ← Python 가상환경 (gitignore)
```

## 4 방식 비교 (프로토타입 핵심)

| 키 | 방식 | 강점 | 약점 |
|---|------|------|------|
| A | Gemini 직접 | 자연어 이해 | API 비용·지연 |
| B | 임베딩 kNN | 한국어 학습 데이터 활용 | 라벨 22건 부족 |
| C | 규칙 엔진 | 빠르고 결정적 | 룰 수동 유지 |
| D | 하이브리드 | C 확정 + B 폴백 | 복잡도 ↑ |

확장 실험 (e~j): adaptive 학습, robust 보정, context 가중, frequency 패턴, integrated, simplified.

## 정식 빌드 계획 (졸작용)

### Phase 1 — 웹 MVP (6~8주, 졸작 시연용)
1. 백엔드 prototype → 정식 코드 정리 (인증·DB 모델·CRUD)
2. 4 방식 (A/B/C/D) API 정리 + 사용자별 데이터 분리
3. Next.js 셋업 + 인증 + 지출 입력 페이지
4. 판정 결과 화면 (시그널 색상, 4 방식 비교, 유사 사례)
5. 히스토리 + 게임화 (스탯·차트·사유 보너스)
6. PWA (홈 화면 추가) + 시드 데이터 + 시연 시나리오

### Phase 2 — 모바일 (졸작 후 4~6주)
- Expo + React Native (Next.js 컴포넌트 70% 재활용)
- 푸시 알림 + TestFlight

## 졸작 차별화 포인트

채점자에게 "단순 LLM 래퍼 아님" 보여주기:
1. 4 방식 비교 (Gemini / 임베딩 kNN / 룰엔진 / 하이브리드)
2. 한국어 임베딩 모델 직접 사용 (`jhgan/ko-sroberta-multitask`)
3. 룰 엔진 8개 + 하이브리드 폴백
4. 게임화 + 스탯 시스템
5. 시연 시 4 방식 결과 나란히 비교

## 운영비 (예상)

- Railway/Render Python hosting: $5/월
- Vercel Next.js: $0 (Hobby)
- Gemini API: $0~10/월 (시연 20명 기준)
- → 합 **$5~15/월**

## 작업 시 주의

- **현재 prototype 상태** — 운영 중인 트리거 X, 자동 실행 X. 코드 자유롭게 수정 가능
- **B 방식 임베딩 모델** 다운로드 필요 (~400MB) — `pip install -r requirements.txt` 시점
- **state/** 안의 JSON 은 적응형 방식이 학습 누적 — 프로덕션엔 DB 로 교체 예정

## 관련

- `~/.claude/CLAUDE.md` 글로벌 시스템 원칙
- 졸작 일정 정해지면 `docs/adr-001-stack-decision.md` 신설 (Stack 결정 기록)
