-- 하루 판정을 1차 판정(PENDING) → 예외 지출 사유 → 확정(FINALIZED) 두 단계로 나눈다 (docs/judgment-flow.md).
-- 지금까지는 판정과 동시에 보상을 줘서, 판정 뒤에 답한 사유가 그날 판정·보상에 닿지 않았다.
-- 보상은 그날 자정에 한 번 지급한다(rewarded_at). 판정 뒤에 지출을 고쳐도 판정 때 정해진 보상은 바뀌지 않는다.
--
-- 기존 행은 모두 보상을 이미 받은 판정이라 FINALIZED 로 두고, 확정·지급 시각은 판정 시각으로 채운다.
-- 1차 결과(initial_*)는 기존 행에 없으므로 NULL — 화면은 1차와 최종을 나눠 보여 주지 않는다.
ALTER TABLE daily_judgments
  ADD COLUMN status ENUM('PENDING','FINALIZED') NOT NULL DEFAULT 'FINALIZED',
  ADD COLUMN initial_signal ENUM('RED','GRAY','GREEN') NULL,
  ADD COLUMN initial_group_details TEXT NULL,
  ADD COLUMN finalized_at DATETIME NULL,
  ADD COLUMN rewarded_at DATETIME NULL;

UPDATE daily_judgments SET finalized_at = judged_at, rewarded_at = judged_at WHERE finalized_at IS NULL;
