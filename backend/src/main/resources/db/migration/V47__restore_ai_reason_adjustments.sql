-- AI가 답변 사유를 인정한 기존 과소비도 다시 최종 판정에 반영한다.
UPDATE expenses e
JOIN ai_inquiries i ON i.expense_id = e.id
SET e.signal_final = CASE i.reason_category
    WHEN 'CEREMONY' THEN 'GREEN'
    WHEN 'EMERGENCY' THEN 'GREEN'
    WHEN 'SELF_INVEST' THEN 'GREEN'
    WHEN 'SOCIAL' THEN 'GRAY'
    ELSE e.signal_initial
END,
i.signal_adjusted = CASE i.reason_category
    WHEN 'CEREMONY' THEN TRUE
    WHEN 'EMERGENCY' THEN TRUE
    WHEN 'SELF_INVEST' THEN TRUE
    WHEN 'SOCIAL' THEN TRUE
    ELSE FALSE
END
WHERE e.signal_initial = 'RED'
  AND i.answered_at IS NOT NULL;
