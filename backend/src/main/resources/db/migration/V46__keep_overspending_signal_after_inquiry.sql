-- 소비 사유 답변은 기록용이다. 과거에 답변으로 완화된 과소비 판정도 최초 판정으로 복원한다.
UPDATE expenses
SET signal_final = signal_initial
WHERE signal_initial = 'RED'
  AND signal_final <> 'RED';

UPDATE ai_inquiries
SET signal_adjusted = FALSE
WHERE signal_adjusted = TRUE;
