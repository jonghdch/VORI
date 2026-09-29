ALTER TABLE attendance_checkins
  ADD COLUMN reward_name VARCHAR(50) NULL,
  ADD COLUMN reward_stat_type ENUM('ENERGY','CHARM','IQ','ENDURANCE') NULL,
  ADD COLUMN reward_stat_delta INT NULL,
  ADD COLUMN streak_count INT NOT NULL DEFAULT 1,
  ADD COLUMN streak_bonus BOOLEAN NOT NULL DEFAULT FALSE;
