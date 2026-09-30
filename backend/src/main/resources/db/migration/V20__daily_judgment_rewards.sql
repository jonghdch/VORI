-- Rewards are determined once by the completed daily judgment's final color.
ALTER TABLE daily_judgments
  ADD COLUMN coin_reward INT NOT NULL DEFAULT 0,
  ADD COLUMN stat_reward_per_type INT NOT NULL DEFAULT 0;

ALTER TABLE pet_growth_logs
  MODIFY COLUMN reason ENUM('EXPENSE_SAVING','GOAL_ACHIEVED','BONUS','DAILY_JUDGMENT') NOT NULL;
