# VORI 도메인 룰

> 비즈 로직·계산식·상태머신·용어집. Service 레이어 짤 때 이 문서 그대로 코드로 옮긴다. AI 가 코드 작성 시 일관성을 확보하기 위한 SSOT.

> ⚠️ **TBD 표시**는 아직 미확정 항목. 임의로 채우지 말 것. 결정되면 이 문서부터 갱신.

## 용어집

| 용어 | 정의 |
|---|---|
| **신호등 (Signal)** | 한 지출의 합리성 판정 결과. `RED` 과지출 / `GRAY` 평소·애매 / `GREEN` 절약 |
| **EMA** | Exponential Moving Average. 사용자의 평소 지출 패턴을 시간 가중 평균으로 추정 |
| **z-score** | 한 지출이 평소 분포 대비 얼마나 이례적인가 (표준편차 단위) |
| **saved_amount** | (평소 평균 - 실제 지출액). 양수 = 절약, 음수 = 과지출 |
| **stat_delta** | 펫 4종 스탯 중 하나의 변동량. 절약 시에만 양수 |
| **stat_type** | 펫 성장 스탯 4종. `ENERGY`(체력) / `CHARM`(매력) / `IQ`(지능) / `ENDURANCE`(인내) |
| **이례 (Anomaly)** | `z-score` 가 임계치 초과한 지출. AI 사유 질문 트리거 대상 |
| **시드 펫 (Starter)** | 회원가입 시 자동 부여되는 펫. `pet_species.is_starter = TRUE` 인 종족 (TBD) |
| **분양 (Release)** | 다 키운 펫을 게임머니로 교환. `pets.released_at` 에 시각 기록 + `release_value` 보상 |

## 핵심 계산식

### 1. saved_amount (절약액)

```
saved_amount = user_stat_stats.mean_ema - expenses.amount
```

- 단위: 사용자의 stat_type 단위 EMA 평균
- 매칭 키: `(user_id, stat_type)` — expenses 의 `stat_type` 컬럼(카테고리에서 derived) 으로 매칭
- 양수 = 평소보다 적게 씀 (절약), 음수 = 평소보다 많이 씀 (과지출)
- expenses 테이블의 `saved_amount` 컬럼에 그대로 저장

### 2. z-score (이례도)

```
z_score = (expenses.amount - user_stat_stats.mean_ema) / user_stat_stats.stddev_ema
```

- 표준편차 단위로 평소와의 거리
- DECIMAL(6,3) 컬럼에 저장

가드:
- `stddev_ema < ε` (예: ε = 0.01) 또는 `sample_count < N` (예: N = 10) 이면 z 계산 스킵
- 스킵 시 `signal_initial = GREEN` 으로 fallback

### 3. 신호등 판정 (signal_initial)

z-score 기반 임계치로 결정. 단, **반복 결제는 RED 자동 제외**.

```
if expenses.is_recurring == TRUE:
    signal_initial = GRAY  # 통신비·구독 등은 사용자의 의식적 결정이 아님 → RED 안 줌

else (일반 지출):
    signal_initial = 
      RED   if z_score >=  Z_RED   (예: +1.5)
      GRAY  if Z_GREEN < z_score < Z_RED
      GREEN if z_score <= Z_GREEN  (예: -0.5)
```

`expenses.memo` 가 채워져 있으면 AI 질문도 스킵 (사용자가 이미 사유 입력) — `signal_final` 보정만 메모 내용 기반으로.

⚠️ TBD: `Z_RED`, `Z_GREEN` 의 정확한 임계값 — 시뮬레이션 데이터로 튜닝 필요. 일단 코드 상수 (`SignalConfig.Z_RED`, `Z_GREEN`) 로 분리해 두기.

### 4. signal_final 보정 (AI 사유 반영)

`signal_initial ∈ {RED, GRAY}` 이면 AI 가 사용자에게 사유를 물음. 사용자 답변을 `reason_category` 로 분류:

| reason_category | signal_initial 가 RED 일 때 | signal_initial 가 GRAY 일 때 |
|---|---|---|
| `CEREMONY` (경조사) | → GRAY 로 완화 | → GREEN 으로 완화 |
| `EMERGENCY` (긴급) | → GRAY 로 완화 | → GREEN 으로 완화 |
| `SOCIAL` (사회생활) | → GRAY 로 완화 | → GRAY 유지 |
| `SELF_INVEST` (자기투자) | → GRAY 로 완화 | → GREEN 으로 완화 |
| `IMPULSE` (충동) | → RED 유지 | → RED 으로 강화 |
| `ETC` (기타) | → RED 유지 | → GRAY 유지 |

⚠️ TBD: 위 표는 임시 매핑. 실제 보정 정책은 비즈 결정 필요. 결정 전엔 코드에 `SignalAdjustmentPolicy` 인터페이스로 분리해서 룰 교체 쉽게.

AI 질문 안 한 경우 `signal_final = signal_initial`.

`signal_adjusted` boolean 컬럼은 보정이 실제로 일어났는지 (initial != final) 표시.

### 5. stat_delta (펫 성장)

```
stat_delta = floor(max(saved_amount, 0) / 1000)
```

- 절약했을 때만 양수, 과지출 시 0 (음수 불가, `INT UNSIGNED`)
- 1000원 절약당 +1
- 대상 스탯: `expenses.stat_type` (카테고리에서 derived 된 캐시 값)
- expenses 테이블의 `stat_delta` 컬럼에 저장 + pets 테이블의 해당 `stat_<type>` 컬럼에 누적 + pet_growth_logs INSERT

### 6. EMA 갱신 (지출 INSERT 시)

```
α (alpha) = 0.1  ← 코드 상수 (EmaConfig.ALPHA 또는 application.properties)
N_MIN = 10       ← 신뢰 가능 표본 수 임계

new_mean   = α × amount + (1 - α) × old_mean
new_var    = α × (amount - old_mean)² + (1 - α) × old_var
new_stddev = √new_var
new_sample_count = old_sample_count + 1
```

- 갱신 단위: `(user_id, stat_type)` — `user_stat_stats` 테이블
- 회원가입 시 4행 미리 INSERT (`mean=0, stddev=0, sample_count=0`)
- `sample_count < N_MIN` 인 동안은 EMA 가 불안정 → z-score 계산 스킵

⚠️ TBD: α 값과 N_MIN 의 최적값은 시뮬레이션으로 튜닝 필요.

## 데이터 변경 흐름 (지출 1건 처리)

지출 INSERT 시 한 트랜잭션 안에서 다음 순서로 처리:

```
1. expenses INSERT (saved_amount, z_score, signal_initial, signal_final, stat_delta 는 일단 NULL/0)
2. user_stat_stats SELECT (해당 stat_type) → mean, stddev, sample_count 읽음
3. z_score 계산 (가드 통과 시)
4. signal_initial 산정
5. saved_amount 계산
6. AI 질문 필요?
   - 조건: signal_initial ∈ {RED, GRAY} AND is_recurring == FALSE AND memo IS NULL
   - YES → ai_inquiries INSERT (status: 대기), signal_final NULL 로 둠
   - NO  → signal_final = signal_initial (반복 결제·메모 입력 시 질문 생략)
7. expenses UPDATE (계산된 값들 set)
8. user_stat_stats UPDATE (EMA 갱신, sample_count++)
9. saved = max(saved_amount, 0)
   - users.total_saved += saved
   - goals UPDATE (해당 year_month, category_id 매칭 + ACTIVE 인 행들 current_amount += saved)
10. stat_delta > 0 면:
    - pets UPDATE (해당 사용자의 활성 펫의 stat_<type> += stat_delta)
    - pet_growth_logs INSERT (reason='EXPENSE_SAVING')
```

AI 답변이 나중에 들어오면:
```
A. ai_inquiries UPDATE (answer_text, reason_category, answered_at, signal_adjusted)
B. expenses UPDATE (signal_final 보정)
```

## 카테고리 → stat_type 매핑

`categories.stat_type` 컬럼이 진실 소스. `expenses.stat_type` 은 INSERT 시 부모 카테고리에서 복사한 캐시.

| 대분류 | stat_type | 의미 |
|---|---|---|
| 식비 | ENERGY | 체력 (먹는 것) |
| 쇼핑 | CHARM | 매력 (꾸미는 것) |
| 뷰티 | CHARM | 매력 (꾸미는 것) |
| 문화 | IQ | 지능 (배우는 것) |
| 여가 | IQ | 지능 (즐기는 것) |
| 생활 | ENDURANCE | 인내 (필수 지출) |
| 고정비 | ENDURANCE | 인내 (의무 지출) |

상세 카테고리는 부모의 `stat_type` 을 상속받음 (시드 시점에 그대로 INSERT).

## 상태머신

### Expense.signal (신호등 전이)

```
[INSERT]
    │
    ▼
signal_initial = ? (z-score 기반)
    │
    ├── GREEN → signal_final = GREEN (AI 질문 X, 종료)
    │
    └── RED|GRAY → AI 질문 (ai_inquiries INSERT)
            │
            ▼
      답변 대기... signal_final = NULL
            │
            ▼ (사용자 답변 들어옴)
      reason_category 분류 → 위 보정 표대로 signal_final 산정
                          → ai_inquiries.signal_adjusted = TRUE (보정 발생 시)
```

### Goal.status

```
[목표 설정] → ACTIVE
    │
    ├── current_amount >= target_amount 도달 시 → DONE (Service 가 매번 체크)
    │
    └── 사용자 포기 → ABANDONED
```

⚠️ TBD: 목표 달성 시 보너스 stat_delta 부여? `pet_growth_logs.reason='GOAL_ACHIEVED'` 인 보상 양 미정.

### Pet.stage

```
INFANT → JUVENILE → ADULT
```

⚠️ TBD: 전이 조건 미정. 후보:
- 총 스탯 합계 기준 (예: 10/50/100)
- 누적 stat_delta 기준
- 일수 경과 기준
- 위 조합

### Pet.released_at (분양)

```
NULL = 활성 펫 (현재 키우는 중)
값 있음 = 분양됨 (다 키워서 처분)
```

⚠️ TBD: 분양 조건. ADULT 단계만? 임의 시점 가능? `release_value` 계산식?

### Egg.opened_at

```
NULL = 미개봉 (인벤토리)
값 있음 = 개봉됨 (이미 펫 부화)
```

상태 전이 트리거: 사용자 개봉 클릭 → `gacha_pulls INSERT` (확률 분포 기반 추첨) + `eggs.opened_at SET` + `pets INSERT (egg_id=...)`. 모두 한 트랜잭션.

### ReceiptOcrJob.status

```
PENDING → PROCESSING → SUCCESS
                    └→ FAILED (error_message 기록)
```

전이 트리거:
- INSERT 시 PENDING
- 비동기 워커가 Vision API 호출 직전 PROCESSING
- 결과 받아 SUCCESS / FAILED
- `completed_at` 도 같이 SET

## 불변 조건 (Invariants)

DB 와 앱이 같이 보장해야 하는 룰:

1. **`monthly_budgets`**: 사용자당 월별 1행. UNIQUE(user_id, year_month) 로 강제
2. **`goals`**: (user_id, year_month, category_id) 조합 UNIQUE. 단 `category_id IS NULL` (전체 목표) 중복은 MySQL UNIQUE+NULL 한계로 DB 가 막지 못함 → **앱(`GoalService.create`) 이 선조회로 보장**
3. **`user_stat_stats`**: 회원가입 시 사용자당 4행 (ENERGY/CHARM/IQ/ENDURANCE) 자동 INSERT. 누락 시 EMA·z 계산 불가
4. **`users.role`**: 시더로 ADMIN 1개. 일반 회원가입은 USER 만 INSERT
5. **`expenses.signal_final`**: NULL 가능 — AI 질문 답변 대기 상태. 화면 표시는 final ?? initial fallback
6. **`expenses.stat_type`**: 카테고리에서 derived 한 캐시. categories.stat_type 과 다르면 부정합 → INSERT 시 자동 복사로 강제
7. **`categories`**: parent_id IS NULL 인 행이 대분류, 값 있는 행이 상세. 깊이는 코드에서 2단으로 제한
8. **`receipts`**: SSOT 는 `receipt_ocr_jobs.receipt_path`. `expenses` 엔 영수증 경로 없음 (변경 이력 #8 결정)
9. **`pets`**: `egg_id UNIQUE` (한 알당 펫 1마리). is_starter 펫은 `egg_id = NULL`

## 비밀번호·인증 정책

- 해시: BCrypt cost 10 (`BCryptPasswordEncoder` 기본)
- 비밀번호 조건: 8자 이상 + 특수문자 1개 이상 (`SignupRequest @Pattern`)
- 평문 비번은 DB·로그·DTO 응답 어디에도 노출 X
- 어드민 비번 `1234` 는 시연·평가 단계 한정. 운영 진입 시 강한 값으로 교체
- 세션 타임아웃: 1시간 (활동 시 리셋)
- Remember-me: 미지원
- 이메일 인증·비번 찾기: 미지원

## 가챠 규칙

⚠️ TBD: 전체 미정.

- `eggs.grade_name` 별 가격 정책?
- `eggs.probability_distribution` JSON 의 스키마? (예: `{"S": 0.05, "A": 0.15, ...}`)
- 가챠 추첨 알고리즘? (단순 weighted random?)
- 변종 (`NORMAL`/`IRO`/`ALIEN`) 등장 확률?
- 시작 펫 부여 규칙? (회원가입 시 자동? C 등급 중 랜덤?)

결정 전엔 `pet/` 도메인 Service 작성 보류.

## 게임머니 정책

⚠️ TBD: 획득·소비 룰 미정.

획득:
- 가입 시 초기 지급? (현재 `users.game_money DEFAULT 0`)
- 절약 보상으로 일정 비율 환산?
- 일일 리포트 보상?

소비:
- 알 구매 (`eggs.price_game_money`)
- 가구 구매 (`user_furniture.price_game_money`)
- 펫 분양은 게임머니 보상 (`pets.release_value`)

## TBD 일괄 (결정 우선순위순)

1. 신호등 임계 (Z_RED, Z_GREEN) — Service 짜기 직전 필요
2. EMA α 값과 N_MIN — 〃
3. 시작 펫 부여 룰 — 회원가입 완성도에 영향
4. signal_final 보정 매핑 — AI 질문 기능 만들기 전 필요
5. Goal 달성 보너스 stat_delta — Goal Service 짜기 전
6. Pet stage 전이 조건 — 펫 성장 로직 짤 때
7. 가챠 확률·알 가격·변종 확률 — Phase 2 시점
8. 게임머니 정책 — 〃
9. 펫 분양 조건·보상식 — 〃

## 관련 문서

- [`README.md`](../README.md) — 도메인 패키지 목록
- [`architecture.md`](architecture.md) — 시스템 경계
- [`backend-flow.md`](backend-flow.md) — 요청 흐름·레이어 책임
- [`db-spec.md`](db-spec.md) — 컬럼·인덱스·FK 명세
- [`../DATABASE.md`](../DATABASE.md) — DB 셋업
