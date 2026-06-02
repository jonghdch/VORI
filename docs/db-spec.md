# VORI DB 테이블 명세

최신 MVP 기준 테이블 명세. 실제 적용된 스키마는 `backend/src/main/resources/db/migration/V1__init.sql`.

본문 = 현재 진실 (최신 컬럼 명세).
하단 변경 이력 (#1 ~ #11) = 각 결정의 사유·스냅샷.

---

## 도메인 1 — 회원·튜토리얼·절약 목표

### 1. 사용자 `users`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 사용자 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 사용자 고유 번호 |
| 이메일 | `email` | UQ | `VARCHAR(100) NOT NULL` | 로그인 이메일 |
| 비밀번호 해시 | `password_hash` |  | `VARCHAR(255) NOT NULL` | bcrypt 등 해시 결과 |
| 닉네임 | `nickname` |  | `VARCHAR(30) NOT NULL` | 화면 표시 이름 |
| 이름 | `name` |  | `VARCHAR(30)` | 튜토리얼 수집값 |
| 나이 | `age` |  | `TINYINT UNSIGNED` | 튜토리얼 수집값 |
| 직업 | `job` |  | `VARCHAR(50)` | 튜토리얼 수집값 |
| 월 소득 | `monthly_income` |  | `INT UNSIGNED` | 소비 기준 참고값 |
| 활성 칭호 식별자 | `active_title_id` | FK | `BIGINT NULL` | → `user_titles(id)` |
| 절약 누적액 | `total_saved` |  | `INT DEFAULT 0` | `saved_amount` 양수 누적 |
| 게임머니 잔액 | `game_money` |  | `INT DEFAULT 0` | 현재 보유 게임머니 |
| 튜토리얼 완료 여부 | `tutorial_done` |  | `BOOLEAN DEFAULT FALSE` | 튜토리얼 완료 여부 |
| 권한 | `role` |  | `ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'` | 사용자 권한. Spring Security 의 `ROLE_USER`/`ROLE_ADMIN` 과 매핑 |
| 이용약관 동의 일시 | `terms_agreed_at` |  | `DATETIME NOT NULL` | 필수 약관 동의 시각 |
| 개인정보 수집·이용 동의 일시 | `privacy_agreed_at` |  | `DATETIME NOT NULL` | 필수 개인정보 동의 시각 |
| 마케팅 정보 수신 동의 일시 | `marketing_agreed_at` |  | `DATETIME NULL` | 선택 동의. NULL이면 미동의 |
| 가입 일시 | `created_at` |  | `DATETIME NOT NULL` | 가입 시각 |

---

### 2. 절약 목표 `goals`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 목표 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 절약 목표 고유 번호 |
| 사용자 식별자 | `user_id` | FK | `BIGINT NOT NULL` | → `users(id)` `ON DELETE CASCADE` |
| 목표 카테고리 | `category_id` | FK | `BIGINT NULL` | → `categories(id)` `ON DELETE SET NULL`. NULL = 전체 지출 대상 |
| 연월 | `year_month` |  | `CHAR(7) NOT NULL` | `YYYY-MM` — 목표가 적용되는 달 |
| 목표 절약 금액 | `target_amount` |  | `INT UNSIGNED NOT NULL` | 목표로 잡은 절약 금액 |
| 현재 절약 금액 | `current_amount` |  | `INT UNSIGNED DEFAULT 0` | `saved_amount` 양수 누적 |
| 진행 상태 | `status` |  | `ENUM('ACTIVE','DONE','ABANDONED') NOT NULL DEFAULT 'ACTIVE'` | 진행 상태 |

인덱스:

```sql
INDEX idx_goals_user_month (user_id, year_month)
INDEX idx_goals_user_category (user_id, category_id)
```

유니크 제약:

```sql
UNIQUE KEY uq_goals_user_month_category (user_id, year_month, category_id)
```

⚠️ MySQL UNIQUE 는 NULL 을 서로 다른 값으로 취급 — `category_id = NULL` (전체 목표) 의 중복은 DB 가 막아 주지 않음. 전체 목표의 월별 1건 제약은 **애플리케이션 레벨**(`GoalService.create` 에서 선조회) 에서 보장.

해석 예시:
- `category_id = NULL`, `year_month = '2026-05'` → "2026년 5월에 10만원 절약하기"
- `category_id = (카페)`, `year_month = '2026-05'` → "2026년 5월에 카페 지출 5만원 절약하기"

---

### 3. 월 예산 `monthly_budgets`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 예산 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 예산 고유 번호 |
| 사용자 식별자 | `user_id` | FK, UQ복합 | `BIGINT NOT NULL` | → `users(id)` |
| 연월 | `year_month` | UQ복합 | `CHAR(7) NOT NULL` | `YYYY-MM` |
| 예산액 | `amount` |  | `INT UNSIGNED NOT NULL` | 해당 월 지출 한도 |

제약:

```sql
UNIQUE(user_id, year_month)
```

---

## 도메인 2 — 소비·판정·리포트

### 4. 카테고리 `categories`

지출 카테고리를 대분류 + 상세 2단 트리로 관리합니다. 대분류와 상세 모두 같은 테이블에 저장합니다.

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 카테고리 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 카테고리 고유 번호 |
| 부모 카테고리 식별자 | `parent_id` | FK | `BIGINT NULL` | → `categories(id)`. NULL이면 대분류 |
| 카테고리 이름 | `name` | UQ복합 | `VARCHAR(30) NOT NULL` | 카테고리 표시 이름 |
| 스탯 유형 | `stat_type` |  | `ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL` | 해당 카테고리가 성장시키는 펫 스탯 |
| 정렬 순서 | `sort_order` |  | `INT DEFAULT 0` | 화면 표시 순서 |
| 활성 여부 | `is_active` |  | `BOOLEAN DEFAULT TRUE` | 비활성화 시 신규 입력에서 숨김 |

제약:

```sql
FOREIGN KEY (parent_id) REFERENCES categories(id)
UNIQUE(parent_id, name)
```

시드 데이터 기준:

| 대분류 | `stat_type` | 상세 예시 |
|---|---|---|
| 식비 | `ENERGY` | 외식, 카페, 배달, 마트·식자재, 편의점 |
| 쇼핑 | `CHARM` | 의류, 신발·잡화, 가방, 액세서리, 생필품·잡화 |
| 뷰티 | `CHARM` | 화장품, 향수, 헤어·미용실, 네일·시술 |
| 문화 | `IQ` | 영화, 공연·뮤지컬, 전시·박물관, 도서 |
| 여가 | `IQ` | 게임·구독, 취미·레저, 여행·숙박, 스포츠·헬스 |
| 생활 | `ENDURANCE` | 대중교통, 택시, 주유, 주차·통행료, 의료·약국 등 |
| 고정비 | `ENDURANCE` | 통신비, 공과금, 주거·관리비, 보험, 구독료, 대출 상환 |

---

### 5. 지출 `expenses`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 지출 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 지출 고유 번호 |
| 사용자 식별자 | `user_id` | FK, IDX | `BIGINT NOT NULL` | → `users(id)` `ON DELETE CASCADE` |
| 지출 일시 | `spent_at` | IDX | `DATETIME NOT NULL` | 지출 시각. 시각 미입력이면 `YYYY-MM-DD 00:00:00` 으로 저장 |
| 시각 입력 여부 | `time_provided` |  | `BOOLEAN NOT NULL DEFAULT FALSE` | TRUE = 사용자가 시각까지 입력, FALSE = 날짜만 |
| 지출 금액 | `amount` |  | `INT UNSIGNED NOT NULL` | 실제 지출액 |
| 결제 수단 | `payment_method` |  | `ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY') NULL` | 현금/체크/신용/이체/모바일페이. 미입력은 NULL |
| 품목 | `item` |  | `VARCHAR(100) NOT NULL` | 지출 항목 |
| 메모 | `memo` |  | `VARCHAR(200) NULL` | 사용자 자발적 메모. 입력해두면 AI 사유 질문 안 함 |
| 반복 결제 | `is_recurring` |  | `BOOLEAN NOT NULL DEFAULT FALSE` | 통신비·구독 등 자동 결제. TRUE 면 신호등 산정 시 RED 자동 제외 |
| 카테고리 식별자 | `category_id` | FK, IDX | `BIGINT NOT NULL` | → `categories(id)` `ON DELETE RESTRICT`. 상세 카테고리 ID 가 기본, AI 가 상세 분류 실패 시 대분류 ID |
| 스탯 유형 (캐시) | `stat_type` | IDX | `ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL` | `categories.stat_type` 을 INSERT 시 복사. JOIN 없이 펫 성장·EMA 갱신용 |
| z-점수 | `z_score` |  | `DECIMAL(6,3)` | 평소 패턴 대비 이례 정도 |
| 초기 신호등 | `signal_initial` |  | `ENUM('RED','GRAY','GREEN')` | 보정 전 판정 |
| 최종 신호등 | `signal_final` |  | `ENUM('RED','GRAY','GREEN')` | AI 사유 반영 후 판정 |
| 스탯 변동값 | `stat_delta` |  | `INT UNSIGNED DEFAULT 0` | 펫 성장량. 절약 시에만 양수, 과지출 시 0 |
| 절약액 | `saved_amount` |  | `INT` | `mean_ema - amount`, 음수 가능 |
| 등록 일시 | `created_at` |  | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` | 행 생성 시각 |
| 수정 일시 | `updated_at` |  | `DATETIME NULL ON UPDATE CURRENT_TIMESTAMP` | 행 갱신 시각 (수정될 때마다 MySQL 자동) |

인덱스:

```sql
INDEX idx_expenses_user_time     (user_id, spent_at)
INDEX idx_expenses_user_category (user_id, category_id)
INDEX idx_expenses_user_stat     (user_id, stat_type, spent_at)
```

FK:

```sql
CONSTRAINT fk_expenses_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
CONSTRAINT fk_expenses_category
  FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
```

(상세 사유는 변경 이력 #3 `expenses` 변경 — 카테고리 컬럼 교체 / #8 `expenses` 컬럼 정리 / #12 일반 가계부 표준 필드 보강 참조)

---

### 6. 수입 `incomes`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 수입 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 수입 고유 번호 |
| 사용자 식별자 | `user_id` | FK, IDX | `BIGINT NOT NULL` | → `users(id)` `ON DELETE CASCADE` |
| 수입 일자 | `received_at` | IDX | `DATE NOT NULL` | 수입 날짜 (시각은 받지 않음) |
| 수입 금액 | `amount` |  | `INT UNSIGNED NOT NULL` | 수입액 |
| 수금 수단 | `payment_method` |  | `ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY') NULL` | expenses 와 같은 ENUM 공유. 미입력은 NULL |
| 출처 | `source` |  | `ENUM('ALLOWANCE','PART_TIME','SCHOLARSHIP','SIDE_JOB','GIFT','INTEREST','OTHER') NOT NULL` | 수입 출처 (대학생 맥락) |
| 반복 수입 | `is_recurring` |  | `BOOLEAN NOT NULL DEFAULT FALSE` | 월급·정기 알바 등 |
| 메모 | `note` |  | `VARCHAR(200) NULL` | 선택 메모 (expenses 의 memo 와 같은 역할, 컬럼명은 기존 유지) |
| 등록 일시 | `created_at` |  | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` | 행 생성 시각 |
| 수정 일시 | `updated_at` |  | `DATETIME NULL ON UPDATE CURRENT_TIMESTAMP` | 행 갱신 시각 (MySQL 자동) |

인덱스:

```sql
INDEX idx_incomes_user_date (user_id, received_at)
```

FK:

```sql
CONSTRAINT fk_incomes_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

(상세 사유는 변경 이력 #9 `incomes` 컬럼 정리 / #12 일반 가계부 표준 필드 보강 참조)

---

### 7. 스탯별 EMA 통계 `user_stat_stats`

스탯 단위로 지출 분포의 EMA 통계를 유지. 두 용도 겸함:
- **펫 스탯 점수 환산** — 절약액 → 스탯 변동(`stat_delta`) 계산
- **이례 감지** — `z_score` 산정·`signal_initial` 판정

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 사용자 식별자 | `user_id` | PK, FK | `BIGINT NOT NULL` | → `users(id)` `ON DELETE CASCADE` |
| 스탯 유형 | `stat_type` | PK | `ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL` | 펫 성장 스탯 4종 |
| EMA 평균값 | `mean_ema` |  | `DECIMAL(12,2) NOT NULL` | 건당 평균 지출액 (EMA) |
| EMA 표준편차 | `stddev_ema` |  | `DECIMAL(12,2) NOT NULL` | 건당 지출 표준편차 (EMA) |
| 표본 수 | `sample_count` |  | `INT NOT NULL DEFAULT 0` | EMA 에 반영된 총 건수 |
| 갱신 일시 | `updated_at` |  | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 행 갱신 시각 (자동) |

제약:

```sql
PRIMARY KEY (user_id, stat_type)
```

FK:

```sql
CONSTRAINT fk_user_stat_stats_user
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
```

EMA 갱신·z 점수 계산·표본 수 가드 등 **계산 로직은 Service 레이어** 책임 (DB 명세 범위 밖).

(상세 사유는 변경 이력 #4 `user_category_stats` → `user_stat_stats` 로 변경 / #10 `user_stat_stats` 컬럼 정리 참조)

---

### 8. AI 사유 질문 `ai_inquiries`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 질문 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 질문 고유 번호 |
| 지출 식별자 | `expense_id` | FK, UQ | `BIGINT NOT NULL` | → `expenses(id)`, 지출 1건당 질문 1개 |
| 사용자 식별자 | `user_id` | FK, IDX | `BIGINT NOT NULL` | → `users(id)`, 사유 사용 횟수 집계용 |
| 질문 내용 | `question` |  | `TEXT NOT NULL` | AI 생성 질문 |
| 답변 텍스트 | `answer_text` |  | `TEXT NULL` | 사용자 답변 |
| 사유 카테고리 | `reason_category` |  | `ENUM('CEREMONY','EMERGENCY','SOCIAL','SELF_INVEST','IMPULSE','ETC') NULL` | 답변 분류 |
| 신호등 보정 여부 | `signal_adjusted` |  | `BOOLEAN DEFAULT FALSE` | 보정 여부 |
| 질문 일시 | `asked_at` |  | `DATETIME NOT NULL` | 질문 생성 시각 |
| 답변 일시 | `answered_at` |  | `DATETIME NULL` | 답변 시각 |

인덱스:

```sql
INDEX(user_id, reason_category, answered_at)
```

사유별 연간 사용 횟수는 별도 테이블에 저장하지 않고 `ai_inquiries`에서 집계합니다.

```sql
SELECT COUNT(*)
FROM ai_inquiries
WHERE user_id = ?
  AND reason_category = ?
  AND answered_at >= '2026-01-01'
  AND answered_at < '2027-01-01';
```

---

### 9. 영수증 OCR 작업 `receipt_ocr_jobs`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| OCR 작업 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | OCR 처리 고유 번호 |
| 지출 식별자 | `expense_id` | FK, IDX | `BIGINT NULL` | → `expenses(id)`, OCR 확인 후 연결 가능 |
| 사용자 식별자 | `user_id` | FK, IDX | `BIGINT NOT NULL` | → `users(id)` |
| 영수증 경로 | `receipt_path` |  | `VARCHAR(255) NOT NULL` | 업로드된 영수증 이미지 경로 |
| OCR 제공자 | `provider` |  | `ENUM('GOOGLE_VISION') NOT NULL DEFAULT 'GOOGLE_VISION'` | Google Vision OCR 사용 |
| 처리 상태 | `status` |  | `ENUM('PENDING','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING'` | OCR 처리 상태 |
| 추출 텍스트 | `extracted_text` |  | `TEXT NULL` | OCR 전체 추출 텍스트 |
| 추출 금액 | `extracted_amount` |  | `INT UNSIGNED NULL` | OCR이 추정한 결제 금액 |
| 추출 일자 | `extracted_date` |  | `DATE NULL` | OCR이 추정한 결제 날짜 |
| 추출 품목 | `extracted_item` |  | `VARCHAR(100) NULL` | OCR이 추정한 대표 품목 |
| 오류 메시지 | `error_message` |  | `TEXT NULL` | 실패 시 오류 내용 |
| 요청 일시 | `requested_at` |  | `DATETIME NOT NULL` | OCR 요청 시각 |
| 완료 일시 | `completed_at` |  | `DATETIME NULL` | OCR 완료 시각 |

인덱스:

```sql
INDEX(user_id, requested_at)
INDEX(expense_id)
```

---

### 10. 일일 리포트 `daily_reports`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 리포트 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 리포트 고유 번호 |
| 사용자 식별자 | `user_id` | FK, UQ복합 | `BIGINT NOT NULL` | → `users(id)` |
| 리포트 일자 | `report_date` | UQ복합 | `DATE NOT NULL` | 리포트 기준일 |
| 수입 합계 | `income_total` |  | `INT UNSIGNED NOT NULL` | 당일 수입 합계 |
| 지출 합계 | `expense_total` |  | `INT UNSIGNED NOT NULL` | 당일 지출 합계 |
| 절약 금액 | `saved_amount` |  | `INT NOT NULL` | 당일 절약 금액 합계 |
| 스탯 변동 합계 | `stat_delta_total` |  | `INT` | 당일 펫 성장량 합계 |
| 펫 상태 스냅샷 | `pet_snapshot` |  | `JSON` | 리포트 생성 시점 펫 상태 |
| AI 코멘트 | `ai_comment` |  | `TEXT` | AI 생성 코멘트 |
| 생성 일시 | `generated_at` |  | `DATETIME NOT NULL` | 생성 시각 |
| 읽은 일시 | `read_at` |  | `DATETIME NULL` | 읽은 시각 |

제약:

```sql
UNIQUE(user_id, report_date)
```

---

## 도메인 3 — 펫·알·가챠·성장

### 11. 펫 도감 `pet_species`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 펫 도감 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 펫 도감 고유 번호 |
| 종족 이름 | `name` | UQ | `VARCHAR(30) NOT NULL` | 펫 종족 이름 |
| 등급 | `tier` |  | `ENUM('STARTER','S','A','B','C') NOT NULL` | 종족 등급 |
| 시작 펫 여부 | `is_starter` |  | `BOOLEAN DEFAULT FALSE` | 시작 펫 여부 |
| 외형 키 | `appearance_key` |  | `VARCHAR(50) NOT NULL` | 프론트 asset 키 |

---

### 12. 펫 `pets`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 펫 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 펫 고유 번호 |
| 사용자 식별자 | `user_id` | FK, IDX | `BIGINT NOT NULL` | → `users(id)` |
| 종족 식별자 | `species_id` | FK | `BIGINT NOT NULL` | → `pet_species(id)` |
| 알 식별자 | `egg_id` | FK, UQ | `BIGINT NULL` | → `eggs(id)`, 시작 펫이면 NULL |
| 부화 일시 | `hatched_at` |  | `DATETIME NULL` | 부화 시각 |
| 에너지 스탯 | `stat_energy` |  | `INT DEFAULT 0` | `ENERGY` 절약 성장값 |
| 매력 스탯 | `stat_charm` |  | `INT DEFAULT 0` | `CHARM` 절약 성장값 |
| 지능 스탯 | `stat_iq` |  | `INT DEFAULT 0` | `IQ` 절약 성장값 |
| 지구력 스탯 | `stat_endurance` |  | `INT DEFAULT 0` | `ENDURANCE` 절약 성장값 |
| 성장 단계 | `stage` |  | `ENUM('INFANT','JUVENILE','ADULT') DEFAULT 'INFANT'` | 성장 단계 |
| 변종 종류 | `variant` |  | `ENUM('NORMAL','IRO','ALIEN') DEFAULT 'NORMAL'` | 변종 |
| 생성 일시 | `created_at` |  | `DATETIME NOT NULL` | 생성 시각 |
| 분양 일시 | `released_at` |  | `DATETIME NULL` | NULL이면 활성 펫 |
| 분양가 | `release_value` |  | `INT UNSIGNED NULL` | 분양 보상 금액 |

인덱스:

```sql
INDEX(user_id, released_at)
```

---

### 13. 알 `eggs`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 알 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 알 고유 번호 |
| 사용자 식별자 | `user_id` | FK | `BIGINT NOT NULL` | → `users(id)` |
| 알 등급 이름 | `grade_name` |  | `VARCHAR(20) NOT NULL` | 초급/중급/고급 등 |
| 가격 | `price_game_money` |  | `INT UNSIGNED NOT NULL` | 구매 당시 알 가격 |
| 확률 분포 | `probability_distribution` |  | `JSON NOT NULL` | 구매 당시 등급별 등장 확률 |
| 구매 일시 | `purchased_at` |  | `DATETIME NOT NULL` | 구매 시각 |
| 개봉 일시 | `opened_at` |  | `DATETIME NULL` | NULL이면 미개봉 |

---

### 14. 가챠 결과 `gacha_pulls`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 가챠 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 가챠 결과 고유 번호 |
| 알 식별자 | `egg_id` | FK, UQ | `BIGINT NOT NULL` | → `eggs(id)` |
| 당첨 종족 식별자 | `drawn_species_id` | FK | `BIGINT NOT NULL` | → `pet_species(id)` |
| 변종 종류 | `variant` |  | `ENUM('NORMAL','IRO','ALIEN') NOT NULL` | 당첨 변종 |
| 추첨 일시 | `drawn_at` |  | `DATETIME NOT NULL` | 추첨 시각 |

---

### 15. 펫 성장 로그 `pet_growth_logs`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 성장 로그 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 성장 기록 고유 번호 |
| 펫 식별자 | `pet_id` | FK, IDX | `BIGINT NOT NULL` | → `pets(id)` |
| 사용자 식별자 | `user_id` | FK, IDX | `BIGINT NOT NULL` | → `users(id)` |
| 지출 식별자 | `expense_id` | FK | `BIGINT NULL` | → `expenses(id)`, 지출 절약으로 성장한 경우 |
| 목표 식별자 | `goal_id` | FK | `BIGINT NULL` | → `goals(id)`, 목표 달성 보너스인 경우 |
| 성장 스탯 | `stat_type` |  | `ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL` | 증가한 스탯 종류 |
| 성장량 | `delta` |  | `INT NOT NULL` | 증가 또는 감소량 |
| 절약액 | `saved_amount` |  | `INT NOT NULL` | 성장 계산에 사용된 절약액 |
| 성장 사유 | `reason` |  | `ENUM('EXPENSE_SAVING','GOAL_ACHIEVED','BONUS') NOT NULL` | 성장 이유 |
| 생성 일시 | `created_at` |  | `DATETIME NOT NULL` | 기록 시각 |

---

## 도메인 4 — 꾸미기·칭호

### 16. 테마 `theme_master`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 테마 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 테마 고유 번호 |
| 테마 이름 | `name` | UQ | `VARCHAR(50) NOT NULL` | 테마 이름 |
| 세트 보너스율 | `set_bonus_pct` |  | `DECIMAL(5,2) DEFAULT 0` | 세트 효과 보너스율 |
| 세트 필요 개수 | `required_count` |  | `TINYINT DEFAULT 3` | 세트 효과 필요 개수 |
| 잠금 해제 칭호 이름 | `unlock_title_name` |  | `VARCHAR(50) NULL` | 이 칭호가 있으면 테마 해제 |

---

### 17. 사용자 가구 `user_furniture`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 보유 가구 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 보유 가구 고유 번호 |
| 사용자 식별자 | `user_id` | FK | `BIGINT NOT NULL` | → `users(id)` |
| 가구 이름 | `name` |  | `VARCHAR(50) NOT NULL` | 보유한 가구 이름 |
| 가구 카테고리 | `category` |  | `ENUM('BED','WALLPAPER','FLOOR','MIRROR','VANITY','PICTURE','BOARD','SHELF','DRAWER','COMPUTER') NOT NULL` | 가구 종류 |
| 대상 스탯 | `stat_target` |  | `ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL` | 보너스 대상 스탯 |
| 분양 보너스율 | `release_bonus_pct` |  | `DECIMAL(5,2) DEFAULT 0` | 분양가 보너스율 |
| 테마 식별자 | `theme_id` | FK | `BIGINT NULL` | → `theme_master(id)` |
| 가격 | `price_game_money` |  | `INT UNSIGNED NOT NULL` | 구매 당시 가구 가격 |
| 배치 X 좌표 | `position_x` |  | `SMALLINT NULL` | NULL이면 인벤토리 |
| 배치 Y 좌표 | `position_y` |  | `SMALLINT NULL` | NULL이면 인벤토리 |
| 획득 일시 | `acquired_at` |  | `DATETIME NOT NULL` | 획득 시각 |

---

### 18. 사용자 칭호 `user_titles`

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 사용자 칭호 식별자 | `id` | PK | `BIGINT AUTO_INCREMENT` | 보유 칭호 고유 번호 |
| 사용자 식별자 | `user_id` | FK, UQ복합 | `BIGINT NOT NULL` | → `users(id)` |
| 칭호 이름 | `name` | UQ복합 | `VARCHAR(50) NOT NULL` | 사용자가 획득한 칭호 이름 |
| 잠금 해제 조건 | `unlock_condition` |  | `JSON NULL` | 칭호 획득 조건 기록용 |
| 잠금 해제 테마 식별자 | `unlocks_theme_id` | FK | `BIGINT NULL` | → `theme_master(id)` |
| 획득 일시 | `acquired_at` |  | `DATETIME NOT NULL` | 획득 시각 |

제약:

```sql
UNIQUE(user_id, name)
```

---

## 핵심 계산 흐름

### 절약 금액 계산

```text
expenses.saved_amount = user_stat_stats.mean_ema - expenses.amount
```

(`user_stat_stats` 는 `(user_id, stat_type)` 단위. `expenses.stat_type` 으로 매칭.)

양수면 절약, 음수면 평균보다 더 쓴 것입니다.

### 절약 목표 반영

```text
saved = max(expenses.saved_amount, 0)
users.total_saved += saved

# 해당 월의 전체 목표
UPDATE goals
   SET current_amount = current_amount + saved
 WHERE user_id    = expenses.user_id
   AND year_month = DATE_FORMAT(expenses.spent_at, '%Y-%m')
   AND category_id IS NULL
   AND status     = 'ACTIVE';

# 해당 월의 같은 카테고리 목표 (대분류·상세 모두)
UPDATE goals
   SET current_amount = current_amount + saved
 WHERE user_id    = expenses.user_id
   AND year_month = DATE_FORMAT(expenses.spent_at, '%Y-%m')
   AND category_id IN (expenses.category_id, parent_of(expenses.category_id))
   AND status     = 'ACTIVE';
```

### 펫 성장 반영

```text
stat_delta = floor(max(expenses.saved_amount, 0) / 1000)
stat_type  = expenses.stat_type   -- categories 에서 derived 캐시
```

성장 스탯은 `expenses.stat_type` 컬럼에서 직접 읽습니다 (JOIN 없음).

| 카테고리 대분류 | `stat_type` | 성장 스탯 |
|---|---|---|
| 식비 | `ENERGY` | 체력 |
| 쇼핑 | `CHARM` | 매력 |
| 뷰티 | `CHARM` | 매력 |
| 문화 | `IQ` | 지능 |
| 여가 | `IQ` | 지능 |
| 생활 | `ENDURANCE` | 인내 |
| 고정비 | `ENDURANCE` | 인내 |

상세 카테고리는 부모 대분류의 `stat_type` 을 상속합니다 (예: `카페` → 식비 → `ENERGY`).

성장 기록은 `pet_growth_logs`에 저장합니다.

---

## 변경 이력 (2026-05-14)

### 1. `users` — 인증·약관 컬럼 추가

기존 명세에 다음 컬럼이 추가됩니다.

| 논리명 | 물리명 | 키 | 타입 | 설명 |
|---|---|---|---|---|
| 비밀번호 해시 | `password_hash` |  | `VARCHAR(255) NOT NULL` | bcrypt 등 해시 결과 |
| 이용약관 동의 일시 | `terms_agreed_at` |  | `DATETIME NOT NULL` | 필수 |
| 개인정보 수집·이용 동의 일시 | `privacy_agreed_at` |  | `DATETIME NOT NULL` | 필수 |
| 마케팅 정보 수신 동의 일시 | `marketing_agreed_at` |  | `DATETIME NULL` | 선택 (NULL = 미동의) |

판단:
- 필수 약관 2종은 `NOT NULL` — 동의 안 하면 회원가입 INSERT 자체가 실패해 데이터 무결성 보장
- 마케팅은 별도 컬럼으로 분리 — 사후 철회 등 부분 변경이 쉬워짐
- 약관 *버전* 추적은 MVP 보류 (필요하면 `user_consents` 별도 테이블로 분리)

### 2. `categories` 신설 — 대분류 + 상세 트리

지출 카테고리는 펫 스탯(4종)과 별개로 두고, 같은 테이블에서 대분류와 상세를 parent_id 트리로 관리합니다.

```sql
CREATE TABLE categories (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id  BIGINT NULL,
  name       VARCHAR(30) NOT NULL,
  stat_type  ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  sort_order INT DEFAULT 0,
  is_active  BOOLEAN DEFAULT TRUE,
  FOREIGN KEY (parent_id) REFERENCES categories(id),
  UNIQUE KEY (parent_id, name)
);
```

- `parent_id IS NULL` → 대분류
- `parent_id` 있는 행 → 상세 (부모 대분류의 자식)
- `stat_type` 은 상세도 부모와 동일하게 시드 (일관성)
- 깊이는 2단으로 제한 (코드에서 컨트롤)

시드 데이터 (대분류 7 + 상세 37 = 44개):

| 대분류 (stat_type) | 상세 |
|---|---|
| 식비 (ENERGY) | 외식 · 카페 · 배달 · 마트·식자재 · 편의점 |
| 쇼핑 (CHARM) | 의류 · 신발·잡화 · 가방 · 액세서리 · 생필품·잡화 |
| 뷰티 (CHARM) | 화장품 · 향수 · 헤어·미용실 · 네일·시술 |
| 문화 (IQ) | 영화 · 공연·뮤지컬 · 전시·박물관 · 도서 |
| 여가 (IQ) | 게임·구독 · 취미·레저 · 여행·숙박 · 스포츠·헬스 |
| 생활 (ENDURANCE) | 대중교통 · 택시 · 주유 · 주차·통행료 · 장거리 교통 · 의료·약국 · 펫용품 · 학용품·문구 · 기타 생활 |
| 고정비 (ENDURANCE) | 통신비 · 공과금 · 주거·관리비 · 보험 · 구독료 · 대출 상환 |

### 3. `expenses` 변경 — 카테고리 컬럼 교체

| 변경 전 | 변경 후 |
|---|---|
| `category ENUM('FOOD','SHOPPING','CULTURE','FIXED') NOT NULL` | `category_id BIGINT NOT NULL` FK → `categories(id)` |
|  | `stat_type ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL` — `categories` 에서 derived 한 캐시 컬럼 |

- `category_id` 는 상세 카테고리 ID 가 기본. AI 가 상세를 확신 못 한 경우 대분류 ID 가 들어감.
- `stat_type` 은 JOIN 없이 펫 스탯 집계·EMA 갱신을 빠르게 하기 위한 캐시.

### 4. `user_category_stats` → `user_stat_stats` 로 변경

EMA 통계는 카테고리 단위가 아니라 스탯 단위로 잡습니다.

```sql
CREATE TABLE user_stat_stats (
  user_id       BIGINT NOT NULL,
  stat_type     ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  mean_ema      DECIMAL(12,2) NOT NULL,
  stddev_ema    DECIMAL(12,2) NOT NULL,
  sample_count  INT NOT NULL DEFAULT 0,
  updated_at    DATETIME NOT NULL
                DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, stat_type),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 5. AI 카테고리 자동 분류 — 2단계 흐름

```text
사용자 입력 텍스트 ("스타벅스 5000")
    ↓
[Step 1] 대분류 분류기 → "식비" (7개 중 하나)
    ↓
[Step 2] 식비 안의 상세 분류기 → "카페" (5개 중 하나)
    ↓
expenses 저장 (category_id = 카페 ID, stat_type = ENERGY)
```

Fallback:
- Step 1·2 모두 성공 → 상세 ID 저장
- Step 1 성공·Step 2 실패 → 대분류 ID 저장 (`category_id` 가 부모를 직접 가리킴)
- Step 1 실패 → UI 에서 사용자에게 대분류 선택 요청

사용자 커스텀 카테고리는 MVP 단계에서 허용하지 않습니다 (시드 데이터만).

### 6. 시드 데이터 작성 방식 — Spring CommandLineRunner

`categories` / `pet_species` / `theme_master` 등 마스터 데이터는 SQL INSERT 가 아니라 Java DAO 코드(Spring `CommandLineRunner`) 로 작성합니다.

```java
@Component
@RequiredArgsConstructor
public class CategorySeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) return;  // 이미 시드됨, 재실행 X

        Category food = categoryRepository.save(Category.parent("식비", StatType.ENERGY, 1));
        // ...
        categoryRepository.saveAll(List.of(
            Category.child(food, "외식", 1),
            Category.child(food, "카페", 2),
            // ...
        ));
    }
}
```

이유:
- 도메인 객체의 검증 로직(`@Valid` 등)을 거치므로 무결성 보장
- 환경별(dev / prod) 다른 시드 가능
- 마이그레이션 도구(Flyway·Liquibase) 없이도 깔끔

### 7. `goals` — 월 단위 목표로 단순화 (날짜 필드 전부 교체)

날짜 범위(`start_date` + `target_date`) 대신 `year_month` 한 컬럼으로 통일. 카테고리도 ENUM → FK.

| 변경 | 컬럼 | 비고 |
|---|---|---|
| 삭제 | `description TEXT` | 자유 텍스트 목표 설명 — MVP 범위 밖 |
| 삭제 | `start_date DATE NOT NULL` | 월 단위 통일로 불필요 |
| 삭제 | `target_date DATE NOT NULL` | 〃 (직전 변경 #7 무효화) |
| 삭제 | `achieved_at DATETIME NULL` | `status = DONE` 이면 충분, 달성 시각 별도 저장 불필요 |
| 추가 | `year_month CHAR(7) NOT NULL` | `YYYY-MM`. `monthly_budgets.year_month` 와 동일 컨벤션 |
| 교체 | `category ENUM(...)` → `category_id BIGINT NULL` | FK → `categories(id) ON DELETE SET NULL`. NULL = 전체 지출 대상 |
| 인덱스 추가 | `idx_goals_user_month (user_id, year_month)` | 월별 목표 조회용 |
| 인덱스 추가 | `idx_goals_user_category (user_id, category_id)` | 카테고리별 목표 조회용 |
| UNIQUE 추가 | `uq_goals_user_month_category (user_id, year_month, category_id)` | 같은 유저·월·카테고리 조합 중복 방지. **단, `category_id IS NULL` 인 전체 목표는 MySQL UNIQUE+NULL 동작 때문에 막히지 않으므로 앱에서 보장** |
| FK 명시 | `user_id` → `users(id) ON DELETE CASCADE` | 유저 탈퇴 시 목표 동반 삭제 |

최종 DDL:

```sql
CREATE TABLE goals (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  category_id     BIGINT NULL,
  year_month      CHAR(7) NOT NULL,
  target_amount   INT UNSIGNED NOT NULL,
  current_amount  INT UNSIGNED DEFAULT 0,
  status          ENUM('ACTIVE','DONE','ABANDONED') NOT NULL DEFAULT 'ACTIVE',
  INDEX idx_goals_user_month (user_id, year_month),
  INDEX idx_goals_user_category (user_id, category_id),
  UNIQUE KEY uq_goals_user_month_category (user_id, year_month, category_id),
  CONSTRAINT fk_goals_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_goals_category
    FOREIGN KEY (category_id) REFERENCES categories(id)
    ON DELETE SET NULL
);
```

⚠️ MySQL UNIQUE 는 NULL 을 서로 다른 값으로 취급하므로 `category_id IS NULL` (전체 목표) 의 월별 1건 제약은 **애플리케이션 레벨**에서 보장.

해석:
- `(user_id=42, category_id=NULL, year_month='2026-05', target_amount=100000)` → "2026년 5월에 10만원 절약하기"
- `(user_id=42, category_id=<카페ID>, year_month='2026-05', target_amount=50000)` → "2026년 5월에 카페 지출 5만원 절약하기"

이유:
- 목표 단위가 **월 1개월**로 고정 → 시작·끝 두 날짜 들고 다닐 필요 없음. `monthly_budgets` 와 동일 패턴
- `category_id` FK 화로 변경 이력 #2 (`categories` 신설) 와 정합 — 상세 카테고리(`카페`) 까지 목표 가능
- `description` 은 화면에서 `year_month + category_id + target_amount` 로 충분히 생성 가능

### 8. `expenses` 컬럼 정리

#3 (카테고리 FK) 이후 남은 4건 정리. 결정 근거: b 단일 `DATETIME`, c 신호등 2종 유지, d 항목별 결정.

| 변경 | 컬럼 | 비고 |
|---|---|---|
| 교체 | `date DATE` + `time TIME NULL` → `spent_at DATETIME NOT NULL` + `time_provided BOOLEAN DEFAULT FALSE` | 시간순 정렬·범위 조회 단순화. 시각 미입력 표시는 boolean 한 컬럼으로 분리 |
| 삭제 | `is_anomaly BOOLEAN` | `signal_initial IN ('RED','GRAY')` 로 derive 가능 — 컬럼 중복 |
| 삭제 | `receipt_path VARCHAR(255)` | 영수증 경로 SSOT 는 `receipt_ocr_jobs.receipt_path` 단일. `expenses` 에 중복 보관 X |
| 좁힘 | `stat_delta INT` → `INT UNSIGNED DEFAULT 0` | 현재 기획상 절약 시에만 양수, 과지출 시 감소 없음 — 음수 불가 |
| 보강 | `created_at DATETIME NOT NULL` → `... DEFAULT CURRENT_TIMESTAMP` | INSERT 시 명시 안 해도 자동 채움 |
| 인덱스 추가 | `idx_expenses_user_time (user_id, spent_at)` | 시간순 타임라인 조회용 |
| 인덱스 추가 | `idx_expenses_user_stat (user_id, stat_type, spent_at)` | 펫 성장·EMA 집계용 |
| FK 명시 | `user_id` → `users(id) ON DELETE CASCADE` | 유저 탈퇴 시 지출 동반 삭제 |
| FK 명시 | `category_id` → `categories(id) ON DELETE RESTRICT` | 카테고리 시드는 보호 — 사용 중인 카테고리 삭제 차단 |

최종 DDL:

```sql
CREATE TABLE expenses (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  spent_at        DATETIME NOT NULL,
  time_provided   BOOLEAN NOT NULL DEFAULT FALSE,
  amount          INT UNSIGNED NOT NULL,
  item            VARCHAR(100) NOT NULL,
  category_id     BIGINT NOT NULL,
  stat_type       ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  z_score         DECIMAL(6,3),
  signal_initial  ENUM('RED','GRAY','GREEN'),
  signal_final    ENUM('RED','GRAY','GREEN'),
  stat_delta      INT UNSIGNED DEFAULT 0,
  saved_amount    INT,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_expenses_user_time     (user_id, spent_at),
  INDEX idx_expenses_user_category (user_id, category_id),
  INDEX idx_expenses_user_stat     (user_id, stat_type, spent_at),
  CONSTRAINT fk_expenses_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_expenses_category
    FOREIGN KEY (category_id) REFERENCES categories(id)
    ON DELETE RESTRICT
);
```

판단 메모:
- `time_provided = FALSE` 인 행은 화면에서 시각 부분(00:00) 을 숨기고 날짜만 표시 — 데이터는 단일 컬럼으로 두되 UI 표현만 분기
- `category_id` FK 의 `ON DELETE RESTRICT` 는 카테고리가 시드 데이터(사용자 생성 X)이기 때문. 시드 운영 중 실수로 카테고리를 지우면 지출이 고아가 되는 사고 방지
- `signal_initial` / `signal_final` 둘 다 유지 — VORI 의 핵심 UX(AI 사유로 판정 보정) 변화를 데이터로 보존

### 9. `incomes` 컬럼 정리

수입은 펫 룰·카테고리 트리 같은 요구사항이 없어 단순 기록 + 통계용. 일관성·UX 정리만.

| 변경 | 컬럼 | 비고 |
|---|---|---|
| 이름 변경 | `date` → `received_at` | `expenses.spent_at` 과 의미 짝. 단, 수입은 시각 입력 UX 없음 → 타입은 `DATE` 유지 |
| 재정의 | `source` ENUM | 한국 대학생 맥락에 맞게 7종으로 변경 |
| 보강 | `created_at DATETIME NOT NULL` → `... DEFAULT CURRENT_TIMESTAMP` | `expenses` 와 동일 |
| 인덱스 명시 | `idx_incomes_user_date (user_id, received_at)` | 월별 조회용 |
| FK 명시 | `user_id` → `users(id) ON DELETE CASCADE` | 유저 탈퇴 시 수입 동반 삭제 |

`source` ENUM 재정의 비교:

| 변경 전 | 변경 후 | 의미 |
|---|---|---|
| `SALARY` | `ALLOWANCE` | 용돈 (대학생 핵심 출처) |
|  | `PART_TIME` | 알바 (신규) |
|  | `SCHOLARSHIP` | 장학금 (신규) |
| `SIDE_JOB` | `SIDE_JOB` | 프리랜서·과외 등 (유지) |
| `GIFT` | `GIFT` | 선물·축의금 등 (유지) |
| `INTEREST` | `INTEREST` | 이자 (유지, 사용 빈도 낮음) |
| `OTHER` | `OTHER` | fallback (유지) |

이유: VORI 타깃이 한국 대학생 → "월급(`SALARY`)" 보다 "용돈(`ALLOWANCE`)" 이 핵심. 알바·장학금 누락이 더 큰 문제였음.

카테고리 트리 (`income_categories` FK) 는 도입하지 않음 — 수입에 펫 스탯·UI 분기 룰이 없어 ENUM 으로 충분.

최종 DDL:

```sql
CREATE TABLE incomes (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  received_at  DATE NOT NULL,
  amount       INT UNSIGNED NOT NULL,
  source       ENUM('ALLOWANCE','PART_TIME','SCHOLARSHIP','SIDE_JOB','GIFT','INTEREST','OTHER') NOT NULL,
  note         VARCHAR(200) NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_incomes_user_date (user_id, received_at),
  CONSTRAINT fk_incomes_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
);
```

### 10. `user_stat_stats` 컬럼 정리

스키마 레벨 정리만. 계산 로직(EMA 갱신·z 점수·0 가드·α 값·행 생성 시점) 은 Service 레이어 책임이라 명세 범위 밖.

| 변경 | 컬럼 | 비고 |
|---|---|---|
| 설명 보강 | `mean_ema`, `stddev_ema` | "건당 평균/표준편차 (EMA)" 로 명시. 갱신 단위 모호함 제거 |
| 이름 변경 | `seed_count` → `sample_count` | "EMA 에 반영된 총 건수". 초기·실지출 구분 X |
| `NOT NULL` 보강 | `sample_count INT DEFAULT 0` → `INT NOT NULL DEFAULT 0` | NULL 들어갈 일 없음 |
| 보강 | `updated_at DATETIME NOT NULL` → `... DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | UPDATE 시 자동 갱신 |
| FK 명시 | `user_id` → `users(id) ON DELETE CASCADE` | 유저 탈퇴 시 EMA 동반 삭제 |

z 점수 (이례 감지) 와 펫 스탯 점수 환산은 **동일 `user_stat_stats` 를 공용**. 별도 카테고리 단위 통계 테이블은 도입하지 않음 — 변경 이력 #4 결정 유지.

Service 레이어로 넘어간 항목 (참고용, 이 문서 범위 밖):
- `UserService.createUser` 트랜잭션에서 4행 미리 INSERT (`mean=0, stddev=0, sample_count=0`)
- `StatScoringService` 가 `stddev_ema < ε` 또는 `sample_count < N` 일 때 z 계산 스킵 + `signal_initial = GREEN` fallback
- EMA `α` 는 `EmaConfig.ALPHA` 또는 `application.properties` 의 `vori.ema.alpha` 로 단일 SSOT

최종 DDL:

```sql
CREATE TABLE user_stat_stats (
  user_id       BIGINT NOT NULL,
  stat_type     ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  mean_ema      DECIMAL(12,2) NOT NULL,
  stddev_ema    DECIMAL(12,2) NOT NULL,
  sample_count  INT NOT NULL DEFAULT 0,
  updated_at    DATETIME NOT NULL
                DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, stat_type),
  CONSTRAINT fk_user_stat_stats_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
);
```

### 11. `users.role` 추가 — 어드민 지정

별도 테이블 분리 없이 `users` 컬럼 한 개로 권한 표현.

| 변경 | 컬럼 | 비고 |
|---|---|---|
| 추가 | `role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'` | 사용자 권한 |

설계 근거:
- boolean (`is_admin`) 대비 ENUM 의 비용은 동일하면서 향후 권한 레벨(`MODERATOR`/`STAFF` 등) 추가가 컬럼 추가 없이 ENUM 값만 추가로 가능
- Spring Security 의 `GrantedAuthority` 와 `"ROLE_" + role` 형식으로 1:1 매핑 — 컨벤션 일치
- 인덱스 추가는 안 함 (어드민 극소수, `WHERE role='ADMIN'` 조회 빈도 낮음)

어드민 지정 운영 방식 (Service 레이어 책임, 명세 범위 밖):
- `CommandLineRunner` 가 `application.properties` 의 `vori.admin.email` 을 읽어 해당 사용자의 `role` 을 `'ADMIN'` 으로 UPDATE
- 시드 시점에 이미 가입돼 있어야 하므로 `UserSeeder` 가 `CategorySeeder` 보다 뒤, 또는 별도 `AdminPromoteRunner` 로 분리

### 12. `expenses` / `incomes` — 일반 가계부 표준 필드 보강 (V2)

VORI 의 AI 사유 질문은 **이례적인 지출에만** 적용. 그 외 입력 흐름은 일반 가계부와 같아야 사용자가 어색하지 않게 쓸 수 있음. 그래서 표준 가계부 필드를 보강.

**`expenses` 추가 컬럼**

| 변경 | 컬럼 | 비고 |
|---|---|---|
| 추가 | `payment_method ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY') NULL` | 결제 수단. 미입력은 NULL (OTHER 옵션 안 둠) |
| 추가 | `memo VARCHAR(200) NULL` | 사용자 자발적 메모. 미리 적어두면 AI 사유 질문 생략 → ai_inquiries 트래픽 감소 |
| 추가 | `is_recurring BOOLEAN NOT NULL DEFAULT FALSE` | 통신비·구독 등 자동 결제. TRUE 면 신호등 산정 시 RED 자동 제외 |
| 추가 | `updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP` | 행 수정 시각 (가계부는 수정 빈번 — 금액 오타·카테고리 변경 등) |

**`incomes` 추가 컬럼** (memo 는 기존 `note` 컬럼이 같은 역할로 유지)

| 변경 | 컬럼 | 비고 |
|---|---|---|
| 추가 | `payment_method ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY') NULL` | expenses 와 같은 ENUM 공유 |
| 추가 | `is_recurring BOOLEAN NOT NULL DEFAULT FALSE` | 월급·정기 알바 등 |
| 추가 | `updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP` | 행 수정 시각 |

설계 근거:
- `payment_method` 에 `OTHER` 안 둠 — 미입력 = NULL 로 처리. 굳이 "기타" 라는 카테고리를 강요하지 않음
- `expenses.memo` vs `incomes.note` 컬럼명 불일치 — 의도된 것. incomes 기존 컬럼 그대로 유지 (기존 데이터·코드 영향 회피). 의미상 동일
- `updated_at` 은 MySQL `ON UPDATE CURRENT_TIMESTAMP` 가 자동 갱신 — Service 레이어가 별도 set 안 해도 됨
- 결제 수단의 `account_name` (계좌·지갑명) 은 보류 — 자유 텍스트화하면 UX 부담, 정규화하면 별도 테이블 필요. 졸작 스코프 외

마이그레이션: `V2__add_payment_memo_recurring.sql`.

V1 으로 만든 행은 `payment_method=NULL`, `memo=NULL`, `is_recurring=FALSE`, `updated_at=NULL` 로 자동 채워짐 (DEFAULT 또는 NULL).
