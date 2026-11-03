-- 기존 예산은 보존한다. 다음 조회 시 이전 계산 버전(1)임을 감지해
-- 해당 사용자·해당 월의 계산 예산만 새 고정비 규칙으로 재생성한다.
ALTER TABLE user_stat_budgets
  ADD COLUMN calculation_version TINYINT UNSIGNED NOT NULL DEFAULT 1;
