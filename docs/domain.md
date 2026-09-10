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
| **시드 펫 (Starter)** | 회원가입 시 자동 부여되는 펫. `pet_species.is_starter = TRUE` 인 종족 = **강아지**(`V7__starter_pet.sql`) |
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

✅ **확정 (2026-09-09)** — 코드 상수가 아니라 **`signal_config` 테이블**에 있다(어드민 조정 가능). 현재 값:

```
z_green = -0.50    z_red = 1.50
```

`V4` 가 심은 초기값은 `z_green = 1.00` 이었는데 위 정의(GREEN = 절약)와 반대였다. 판정식이 `z <= zGreen ? GREEN : ...` 이라 **평균 + 1σ 까지 전부 GREEN** 이 됐고, 정규분포 기준 약 84% 가 초록불이었다. `V10__signal_threshold_fix.sql` 로 스펙값을 복구했다.

### 4. signal_final 보정 (AI 사유 반영)

**`signal_initial == RED` 이면** AI 가 사용자에게 사유를 물음. 사용자 답변을 `reason_category` 로 분류:

✅ **확정 (2026-09-09)** — 2단계 완화가 아니라 **한 번에 결정**한다 (`AiInquiryService.computeSignalFinal`).

| reason_category | 결과 |
|---|---|
| `CEREMONY` (경조사) · `EMERGENCY` (긴급) · `SELF_INVEST` (자기투자) | → **GREEN** |
| `SOCIAL` (사회생활) | → **GRAY** |
| `IMPULSE` (충동) · `ETC` (기타) | → 원래 값 유지 |

**질문 조건이 `{RED, GRAY}` 에서 `RED` 로 좁혀졌다.** `z_green` 을 스펙값(-0.5)으로 되돌리자 GRAY 가 평균 이하 구간까지 품게 되어, 평소보다 적게 쓴 지출에도 *"평균보다 높습니다"* 라고 묻는 상황이 생겼기 때문이다. 용어집의 "이례 = 임계치를 **초과**한 지출" 정의와도 RED 쪽이 맞고, 질문 빈도가 69% → 7% 로 내려간다.

→ 그 결과 위 표의 `GRAY` 출발 경로는 현재 발생하지 않는다.

AI 질문 안 한 경우 `signal_final = signal_initial`.

`signal_adjusted` boolean 컬럼은 보정이 실제로 일어났는지 (initial != final) 표시.

### 4-1. 인정 보상 (2026-09-09 추가)

판정만 바뀌고 보상이 그대로면 사용자가 AI 에게 이유를 설명할 이유가 없다. 그래서 **인정받은 과지출에는 보상을 준다.**

```
reason ∈ {CEREMONY, EMERGENCY, SELF_INVEST}  AND  saved_amount < 0
    → 펫 스탯 +10, 게임머니 +100
    → pet_growth_logs INSERT (reason = 'BONUS')
```

- `SOCIAL` 은 제외한다 — "회식이었어요" 로 매번 보상받으면 판정이 핑계 대기 게임이 된다.
- 절약한 지출(`saved_amount >= 0`)은 등록 시점에 이미 보상받았으므로 제외.
- **감점이 아니라 가산이다.** 과지출은 애초에 감점이 아니라 무보상이고(`max(saved,0)`), 코인 회수는 잔액이 음수가 될 수 있으며, `expenses.stat_delta` 가 `INT UNSIGNED` 라 음수 기록이 불가능하다.
- 보상이 답변보다 먼저 나가는 비동기 구조라 사후 정산이 될 수밖에 없다.

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
α (alpha) = 0.2  ← ExpenseService.EMA_ALPHA
N_MIN = 5        ← ExpenseService.N_MIN (신뢰 가능 표본 수 임계)

new_mean   = α × amount + (1 - α) × old_mean
new_var    = α × (amount - old_mean)² + (1 - α) × old_var
new_stddev = √new_var
new_sample_count = old_sample_count + 1
```

- 갱신 단위: `(user_id, stat_type)` — `user_stat_stats` 테이블
- 회원가입 시 4행 미리 INSERT (`mean=0, stddev=0, sample_count=0`)
- `sample_count < N_MIN` 인 동안은 EMA 가 불안정 → z-score 계산 스킵

✅ **확정 (구현 반영)** — 기획 초안은 α 0.1 · N_MIN 10 이었으나 구현은 **0.2 · 5** 다. 문서를 코드에 맞췄다.

⚠️ **알려진 한계** — α 0.2 는 최근 5건 정도가 평균의 대부분을 차지하는 값이라 **계절 변동에 취약하다.** 대학생 타깃 특성상 방학 동안 적게 쓰면 `mean_ema` 가 크게 낮아지고, 개강 후 정상 소비만 해도 z-score 가 치솟아 RED 가 연달아 뜬다. 학기/방학 모드나 α 조절은 향후 과제. 자세한 내용은 [`plan-vs-reality.md`](plan-vs-reality.md).

⚠️ **온보딩 부재와 맞물린 문제** — `sample_count < 5` 인 동안 z-score 를 건너뛰고 GREEN 으로 fallback 하므로, **신규 사용자는 첫 5건 동안 판정을 전혀 경험하지 못한다.** `users` 에 `age`·`job`·`monthly_income`·`tutorial_done` 컬럼은 준비돼 있으나 이를 채우는 흐름이 없다.

## 데이터 변경 흐름 (지출 1건 처리)

지출 INSERT 시 한 트랜잭션 안에서 다음 순서로 처리:

```
1. expenses INSERT (saved_amount, z_score, signal_initial, signal_final, stat_delta 는 일단 NULL/0)
2. user_stat_stats SELECT (해당 stat_type) → mean, stddev, sample_count 읽음
3. z_score 계산 (가드 통과 시)
4. signal_initial 산정
5. saved_amount 계산
6. AI 질문 필요?
   - 조건: signal_initial == RED AND is_recurring == FALSE
   - YES → ai_inquiries INSERT (status: 대기), signal_final NULL 로 둠
   - NO  → signal_final = signal_initial (반복 결제 시 질문 생략)
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
    ├── GREEN|GRAY → signal_final = signal_initial (AI 질문 X, 종료)
    │
    └── RED → AI 질문 (ai_inquiries INSERT)
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

✅ **확정** — **스탯 총합 기준. 상향 전이만 일어난다** (`Pet.evaluateStage`).

```
INFANT     스탯 총합 0 ~ 199
JUVENILE   스탯 총합 200 이상
ADULT      스탯 총합 300 이상   ← 분양 가능
```

기획 초안은 200 / 400 / 600 이었으나 구현은 **200 / 300** 이다.

⚠️ **알려진 한계 — 성장 곡선** — 스탯에 **상한이 없고** `stat_delta = 절약액 / 1000` 이라, 큰 절약 한 번이면 수백 점이 오른다. 시연 계정 실측으로 **지출 9건에 스탯 730** 이 나왔고, 지출 2건만으로도 성체에 도달할 수 있다. 기획이 상정한 "1주일 체험" 페이싱이 사실상 사라졌다. 다만 게임 경제가 깨지지는 않는다 — 스탯 730 을 만들려면 누적 절약 73만원이 필요하고 그 과정에서 이미 코인 73,000 을 받으므로, 분양가(7,300)는 그 10% 수준의 보너스다. 재조정은 최종발표 전 과제.

### Pet.released_at (분양)

```
NULL = 활성 펫 (현재 키우는 중)
값 있음 = 분양됨 (다 키워서 처분)
```

✅ **확정** — **ADULT 단계만 분양 가능** (`PetService.release`, 아니면 400).

```
release_value = 스탯총합 × 10 × (1 + (개별 가구 보너스합 + 테마 세트 보너스합) / 100)
```

- **마이룸에 배치된**(`position_x/y` 둘 다 NOT NULL) 가구만 계산에 들어간다. 인벤토리에 쌓아둔 가구는 제외 — 꾸며야 이득이라는 게 보상 설계 의도.
- 개별 보너스는 `user_furniture.release_bonus_pct` 합(가구당 1.00~4.00%).
- 세트 보너스는 같은 `theme_id` 를 `theme_master.required_count` 이상 배치했을 때 `set_bonus_pct` 가산(우드 8% / 코지 12% / 스터디 15%).
- BigDecimal 로 계산 후 소수점 버림.
- 잔액 변경 경로라 사용자 행을 잠그고 읽는다(`findByIdForUpdate`).

실측 예: 스탯 730, 책장(2.00%)·서랍장(2.00%) 배치 → `730 × 10 × 1.04 = 7,592`

### Egg.opened_at

```
NULL = 미개봉 (인벤토리)
값 있음 = 개봉됨 (이미 펫 부화)
```

상태 전이 트리거: 사용자 개봉 클릭 → `gacha_pulls INSERT` (확률 분포 기반 추첨) + `eggs.opened_at SET` + `pets INSERT (egg_id=...)`. 모두 한 트랜잭션.

### ReceiptOcrJob.status

```
PROCESSING → SUCCESS
          └→ FAILED (error_message 기록)
```

✅ **확정 (구현 반영)** — Google Vision 이 아니라 **Gemini 이미지 입력**을 쓰고, 비동기 워커가 아니라 **동기 처리**다.

- INSERT 시 바로 `PROCESSING` (PENDING 단계를 쓰지 않는다)
- **Gemini 호출은 트랜잭션 밖**에서 한다 — 응답이 18~31초라 그동안 DB 커넥션을 물고 있으면 동시 업로드 몇 건에 풀이 마른다
- 결과 받아 짧은 쓰기 트랜잭션으로 SUCCESS / FAILED + `completed_at` SET
- 실패해도 예외를 밖으로 던지지 않고 FAILED 로 남겨 이력을 추적한다
- `status = SUCCESS` 여도 개별 필드는 NULL 일 수 있다(흐린 영수증). 화면은 읽힌 값만 채우고 나머지는 사용자가 입력

## 불변 조건 (Invariants)

DB 와 앱이 같이 보장해야 하는 룰:

1. **`monthly_budgets`**: 사용자당 월별 1행. UNIQUE(user_id, year_month) 로 강제
2. **`goals`**: (user_id, year_month, category_id) 조합 UNIQUE. 단 `category_id IS NULL` (전체 목표) 중복은 MySQL UNIQUE+NULL 한계로 DB 가 막지 못함 → **앱(`GoalService.create`) 이 선조회로 보장**
3. **`user_stat_stats`**: 회원가입 시 사용자당 4행 (ENERGY/CHARM/IQ/ENDURANCE) 자동 INSERT. 누락 시 EMA·z 계산 불가
4. **`users.role`**: 시더로 ADMIN 1개. 일반 회원가입은 USER 만 INSERT
5. **`expenses.signal_final`**: NULL 가능 — AI 질문 답변 대기 상태. 화면 표시는 final ?? initial fallback
6. **`expenses.stat_type`**: 카테고리에서 derived 한 캐시. categories.stat_type 과 다르면 부정합 → INSERT 시 자동 복사로 강제
7. **`categories`**: parent_id IS NULL 인 행이 대분류, 값 있는 행이 상세. 깊이는 코드에서 2단으로 제한
8. **`receipts`**: **이미지를 저장하지 않는다.** 메모리에서 Gemini 로 넘기고 결과만 남긴다 — 가계부에 필요한 건 추출된 데이터이고, 영수증에는 카드번호 뒷자리 같은 정보가 남아 있다. `receipt_ocr_jobs.receipt_path` 는 `V8` 에서 NULL 허용으로 바뀌었다
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

✅ **확정 (2026-08-25 팀 합의 + 구현)** — 마스터 데이터는 테이블이 아니라 `EggGrade` enum 에 있다.

| 등급 | 이름 | 가격 | S | A | B | C |
|---|---|---|---|---|---|---|
| `BASIC` | 기본 알 | 2,500 | 1% | 5% | 24% | 70% |
| `PREMIUM` | 고급 알 | 6,000 | 5% | 20% | 45% | 30% |
| `LEGENDARY` | 최고급 알 | 15,000 | 20% | 50% | 30% | 0% |

- **확률 분포는 구매 시점에 `eggs.probability_distribution` JSON 으로 박제**된다(`{"S":1,"A":5,...}` — 가중치 정수). 이후 `EggGrade` 를 고쳐도 이미 팔린 알의 확률은 변하지 않는다.
- 추첨은 가중치 기반 랜덤(`GachaService`). 등급을 뽑고 그 등급의 종족 중에서 다시 고른다.
- **변종**: `NORMAL` 90 / `IRO` 8 / `ALIEN` 2.
- **시작 펫**: 회원가입 시 `pet_species.is_starter = TRUE` 인 종족(**강아지**)을 자동 지급. `V7__starter_pet.sql` 에서 지정했다 — `PetSpeciesSeeder` 가 테이블이 비어 있을 때만 시드하므로 마이그레이션으로 넣어야 했다.
- **개봉 조건**: 안 깐 펫이 이미 있으면 **409**. 화면과 성장 로직이 모두 활성 펫 한 마리만 다루기 때문에, 그냥 두면 새 펫이 보이지도 자라지도 않고 코인만 사라진다. 구매는 막지 않는다.

> 📌 확률형 아이템 표시 의무(2024.3 시행)는 **유상** 아이템이 대상이라 현재 VORI 는 해당 없다(코인은 절약으로만 획득). 다만 상점 화면에 확률을 이미 노출하고 있다. 코인을 현금으로 파는 순간 적용 대상이 된다.

## 게임머니 정책

✅ **확정 (2026-08-25 팀 합의 + 구현)**

**획득**

| 경로 | 양 | 위치 |
|---|---|---|
| **절약** | 절약액 ÷ 10 (10원 절약당 1코인) | `ExpenseService.SAVED_PER_GAME_MONEY` |
| 펫 분양 | 스탯총합 × 10 × (1 + 보너스) | `PetService.release` |
| 인정 보상 | +100 (합리적 과지출로 인정될 때) | `AiInquiryService` |
| 가입 시 초기 지급 | **없음** (`DEFAULT 0`) | — |
| 일일 리포트 보상 | **없음** | — |

**소비**

- 알 구매 (`EggGrade.price` — 2,500 / 6,000 / 15,000)
- 가구 구매 (`FurnitureCatalog.price` — 2,000 ~ 12,000)

**설계 의도** — 기본 알이 2,500 코인이므로 **25,000원 절약 = 알 1개**다. 게임 루프가 며칠 단위로 돌게 잡은 값이고, 밸런싱은 `SAVED_PER_GAME_MONEY` 상수 하나만 바꾸면 된다. 분양 보상은 그 위에 얹히는 보너스 성격이다.

> 📌 **이 전환이 없으면 게임이 시작되지 않는다.** 초기 구현에는 게임머니를 늘리는 코드가 어디에도 없었고 `is_starter` 종족도 전부 FALSE 라, 알 구매 → 펫 획득 → 분양 루프의 진입점이 아예 없었다(데드락). 절약→코인 전환과 시작 펫으로 해소했다.

**잔액 변경은 모두 행 잠금**(`UserRepository.findByIdForUpdate`)으로 읽는다 — 더블클릭 이중 차감 방지.

## TBD 현황 (2026-09-09 갱신)

1학기에 남겨둔 TBD 9개 중 **8개가 결정·구현됐다.** 이 문서를 그때 상태로 두면 잘못된 값을 근거로 코드를 짜게 되므로(실제로 "충동 = −10 감점" 이 존재하는 줄 알고 설계를 시작한 일이 있었다) 위 각 절에 확정 내용을 반영했다.

| 항목 | 상태 |
|---|---|
| 신호등 임계 (Z_RED, Z_GREEN) | ✅ `signal_config` 테이블, 1.50 / −0.50 |
| EMA α 값과 N_MIN | ✅ 0.2 / 5 |
| 시작 펫 부여 룰 | ✅ 강아지, `V7__starter_pet.sql` |
| signal_final 보정 매핑 | ✅ 한 번에 결정하는 방식으로 확정 |
| Pet stage 전이 조건 | ✅ 스탯 총합 200 / 300, 상향 전용 |
| 가챠 확률·알 가격·변종 확률 | ✅ `EggGrade` + 변종 90/8/2 |
| 게임머니 정책 | ✅ 10원 절약당 1코인 |
| 펫 분양 조건·보상식 | ✅ ADULT 한정, 스탯총합 × 10 × (1 + 보너스) |
| **Goal 달성 보너스 stat_delta** | ⚠️ **여전히 미결** — `Goal.addCurrentAmount` 에 TODO 로 남아 있다 |

### 남은 결정 · 향후 과제

| | 내용 | 시점 |
|---|---|---|
| ⚠️ | Goal 달성 시 보너스 stat_delta 양 | 기획 확정 시 |
| ⚠️ | 성장 곡선 재조정 (스탯 상한 없음, 2건이면 성체) | 최종발표 전 |
| ⚠️ | 온보딩 — `age`·`job`·`monthly_income`·`tutorial_done` 을 채우는 흐름 | 최종발표 전 |
| ⚠️ | 계절성 대응 (학기/방학 모드 또는 α 조절) | 향후 |
| ⚠️ | 테마 세트 보너스와 `user_titles.unlocks_theme_id` 의 관계 정리 | 여유 시 |

**구현이 기획과 달라진 항목 전체 목록은 [`plan-vs-reality.md`](plan-vs-reality.md) 에 있다.** 최종보고서의 "설계와 달라진 부분" 절은 그 문서를 근거로 쓰면 된다.

## 관련 문서

- [`README.md`](../README.md) — 도메인 패키지 목록
- [`architecture.md`](architecture.md) — 시스템 경계
- [`backend-flow.md`](backend-flow.md) — 요청 흐름·레이어 책임
- [`db-spec.md`](db-spec.md) — 컬럼·인덱스·FK 명세
- [`../DATABASE.md`](../DATABASE.md) — DB 셋업
- [`plan-vs-reality.md`](plan-vs-reality.md) — 1학기 기획과 달라진 부분 (최종보고서용)
- [`api-integration.md`](api-integration.md) — 프론트 연동 명세 · 실제 응답 예시
- [`demo-plan.md`](demo-plan.md) — 중간발표 시연 각본 · 연동 우선순위
- [`market-research.md`](market-research.md) — 시장·경쟁 분석
