-- 지출 기록 습관 보상(코인 100)을 하루 한 번만 주기 위한 마지막 지급일 (User.grantDailyRecordReward).
-- 등록마다 주고 삭제해도 회수하지 않아 등록→삭제 반복으로 코인이 무한히 쌓였다.
ALTER TABLE users
  ADD COLUMN last_record_reward_on DATE NULL;
