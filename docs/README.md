# VORI 문서 안내

문서가 설명하는 시점과 목적을 구분하기 위한 인덱스다. 현재 동작을 확인할 때는 **현재 구현 기준** 문서를
먼저 보고, 기획·발표 문서의 구현 상태 문장은 작성 시점의 기록으로 읽는다.

## 현재 구현 기준

| 문서 | 책임 |
|---|---|
| [`architecture.md`](architecture.md) | 실행 구조, 라우트, 외부 시스템, 큰 데이터 흐름 |
| [`backend-flow.md`](backend-flow.md) | 백엔드 레이어와 요청 처리 규칙 |
| [`domain.md`](domain.md) | 확정된 비즈니스 규칙과 용어(SSOT) |
| [`db-spec.md`](db-spec.md) | 현재 DB 테이블·컬럼 명세와 변경 이력 |
| [`api-integration.md`](api-integration.md) | 프론트와 백엔드 API 연결 방법 |
| [`../DATABASE.md`](../DATABASE.md) | 로컬 DB 설정·Flyway 운영 방법 |

현재 동작과 문서가 다르면 코드와 Flyway 마이그레이션을 우선 확인하고 위 문서를 함께 갱신한다.

## UX·기능 설계

| 문서 | 책임 |
|---|---|
| [`signup-flow.md`](signup-flow.md) | 회원가입·소비 프로필 수집 흐름 |
| [`tutorial-flow.md`](tutorial-flow.md) | 온보딩과 인앱 튜토리얼 설계 |
| [`judgment-flow.md`](judgment-flow.md) | 하루 판정 흐름 재설계 — 1차 판정 → 예외 지출 사유 → 확정·보상 |

이 문서에는 구현 전 제안도 포함될 수 있다. 구현 여부는 `App.js`의 라우트와 해당 페이지 코드를 함께 확인한다.

## 계획·분석

| 문서 | 책임 |
|---|---|
| [`roadmap.md`](roadmap.md) | 설계 철학, 결정 대기 항목, 향후 우선순위 |
| [`market-research.md`](market-research.md) | 시장·경쟁 분석과 기능 결정의 근거 |
| [`plan-vs-reality.md`](plan-vs-reality.md) | 초기 기획과 구현 결과 대조 |

날짜가 적힌 분석은 그 시점의 스냅샷이다. 해결된 문제는 원문을 지우지 않고 상단에 현재 상태를 덧붙인다.

## 11월 시연

| 문서 | 책임 |
|---|---|
| [`final-demo-plan.md`](final-demo-plan.md) | 최종발표(11/4)·경진대회(11/12) 시연 각본·무대 규칙·계정 세팅·Gemini 한도 운영 |

## 발표·과거 기록

| 문서 | 책임 |
|---|---|
| [`demo-plan.md`](demo-plan.md) | 중간발표 시연 동선 |
| [`demo-recording.md`](demo-recording.md) | 시연 영상 녹화 체크리스트 |
| [`midterm-script.md`](midterm-script.md) | 중간발표 대본 |
| [`midterm-slides.md`](midterm-slides.md) | 중간발표 슬라이드 원고 |
| [`demo-plan-history.md`](demo-plan-history.md) | 폐기안·과거 리허설 기록 |

이 그룹은 당시 발표 재현을 위한 기록이며 현재 기능 명세로 사용하지 않는다.
