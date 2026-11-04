-- 그룹 질문은 특정 지출 한 건과 분리해, 같은 그룹의 개별 질문과 함께 존재할 수 있게 한다.
ALTER TABLE ai_inquiries
  MODIFY COLUMN expense_id BIGINT NULL,
  ADD COLUMN group_date DATE NULL AFTER group_amount;
