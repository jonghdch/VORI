-- 회원가입 직후 5단계 선택형 소비 프로필.
-- 실제 지출 내역을 만들지 않고, 신규 사용자의 초기 판정 기준선과 온보딩 개인화에만 쓴다.
CREATE TABLE user_spending_profiles (
  user_id                BIGINT PRIMARY KEY,
  monthly_budget_band    VARCHAR(30) NOT NULL,
  meal_cost_band         VARCHAR(30) NOT NULL,
  primary_spend_area     VARCHAR(30) NOT NULL,
  spending_habit         VARCHAR(30) NOT NULL,
  monthly_goal           VARCHAR(30) NOT NULL,
  baseline_applied       BOOLEAN NOT NULL DEFAULT FALSE,
  created_at             DATETIME NOT NULL,
  updated_at             DATETIME NOT NULL,
  CONSTRAINT fk_user_spending_profiles_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
