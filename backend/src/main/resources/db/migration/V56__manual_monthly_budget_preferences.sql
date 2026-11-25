-- 월 수입은 생활 정보로 보관하고, 가계부의 실제 월 예산은 사용자가 별도로 정한다.
-- focus 값은 ENERGY,CHARM,IQ,ENDURANCE 중 복수 선택을 쉼표로 저장한다.
ALTER TABLE users
  ADD COLUMN monthly_budget INT UNSIGNED NULL AFTER monthly_income,
  ADD COLUMN budget_focus_stats VARCHAR(80) NULL AFTER monthly_budget;
