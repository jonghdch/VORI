-- NULL means legacy rewards were not recorded; do not invent historical payouts.
ALTER TABLE expenses
  ADD COLUMN coin_reward INT NULL,
  ADD COLUMN granted_stat_delta INT NULL,
  ADD COLUMN reward_stat_type VARCHAR(20) NULL;
