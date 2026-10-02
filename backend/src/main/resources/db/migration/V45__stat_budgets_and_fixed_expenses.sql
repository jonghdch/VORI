CREATE TABLE IF NOT EXISTS user_stat_budgets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  `year_month` CHAR(7) NOT NULL,
  stat_type ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  amount INT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_stat_budget (user_id, `year_month`, stat_type),
  CONSTRAINT fk_user_stat_budget_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS fixed_expenses (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  amount INT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_fixed_expense_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

ALTER TABLE daily_judgments ADD COLUMN reward_details VARCHAR(255) NULL;
