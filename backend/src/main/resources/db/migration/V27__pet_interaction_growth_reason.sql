-- 펫 상호작용(우클릭) 매력 보너스를 다른 BONUS 와 구분한다 (GrowthReason.PET_INTERACTION).
-- 상호작용 API 는 호출 횟수 제한이 없어, 하루에 오를 수 있는 횟수(PetService.INTERACT_CHARM_DAILY_CAP)를
-- 이 사유의 성장 기록으로 센다. 이전 상호작용 기록은 BONUS 로 남아 있으나 상한 계산은 오늘 기록만 본다.
ALTER TABLE pet_growth_logs
  MODIFY COLUMN reason ENUM('EXPENSE_SAVING','GOAL_ACHIEVED','BONUS','DAILY_JUDGMENT','ATTENDANCE_ITEM','FURNITURE_BONUS','PET_INTERACTION') NOT NULL;
