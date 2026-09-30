-- 평균·편차가 온보딩 설문으로 채운 초기값인지 표시한다 (UserStatStats.seeded, BaselineSeeder).
-- 표본 수만으로는 "씨딩값(표본 5)" 과 "실제 지출 5건" 을 구분할 수 없어 실제 기록을 덮어쓰는 문제가 있었다.
ALTER TABLE user_stat_stats
  ADD COLUMN seeded BOOLEAN NOT NULL DEFAULT FALSE;

-- 이미 씨딩된 행 채우기 — 씨딩값의 "지문" 으로 찾는다.
-- 씨딩값은 규칙이 정해져 있어 표본 5 + 평균이 그 규칙값과 소수점까지 같으면 씨딩된 행이다.
-- (지출 행 유무나 baseline_applied 로 추측하면, 지출을 지운 사용자나 프로필 설정에서
--  월 수입만 바꿔 씨딩된 사용자를 잘못 분류한다)
--
-- 쇼핑·문화·생활: 평균 = 월 수입 × 비율 (BaselineSeeder.TYPICAL_RATE, 소수 둘째 자리 반올림)
UPDATE user_stat_stats s
  JOIN users u ON u.id = s.user_id
SET s.seeded = TRUE
WHERE s.sample_count = 5
  AND u.monthly_income > 0
  AND (   (s.stat_type = 'CHARM'     AND s.mean_ema = ROUND(u.monthly_income * 0.020, 2))
       OR (s.stat_type = 'IQ'        AND s.mean_ema = ROUND(u.monthly_income * 0.012, 2))
       OR (s.stat_type = 'ENDURANCE' AND s.mean_ema = ROUND(u.monthly_income * 0.040, 2)));

-- 식비: 설문의 한 끼 구간 중앙값·편차 (MealCostBand.midpoint / initialStddev)
UPDATE user_stat_stats s
  JOIN user_spending_profiles p ON p.user_id = s.user_id
SET s.seeded = TRUE
WHERE s.stat_type = 'ENERGY'
  AND s.sample_count = 5
  AND (   (p.meal_cost_band = 'UNDER_7'    AND s.mean_ema = 6000  AND s.stddev_ema = 1500)
       OR (p.meal_cost_band = 'MEAL_7_10'  AND s.mean_ema = 8500  AND s.stddev_ema = 2000)
       OR (p.meal_cost_band = 'MEAL_10_15' AND s.mean_ema = 12500 AND s.stddev_ema = 2800)
       OR (p.meal_cost_band = 'OVER_15'    AND s.mean_ema = 18000 AND s.stddev_ema = 4000));
