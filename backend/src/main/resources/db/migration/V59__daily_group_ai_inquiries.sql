-- 일일 예산을 초과한 스탯 그룹도 한 번의 사유 질문으로 재판정할 수 있게 한다.
ALTER TABLE ai_inquiries
  ADD COLUMN daily_group_inquiry BOOLEAN NOT NULL DEFAULT FALSE AFTER expense_id,
  ADD COLUMN group_stat_type ENUM('ENERGY','CHARM','IQ','ENDURANCE') NULL AFTER daily_group_inquiry,
  ADD COLUMN group_amount INT UNSIGNED NULL AFTER group_stat_type;
