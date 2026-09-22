CREATE TABLE daily_judgments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  judgment_date DATE NOT NULL,
  result_signal ENUM('RED','GRAY','GREEN') NOT NULL,
  expense_count INT NOT NULL DEFAULT 0,
  judged_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_daily_judgment_user_date (user_id, judgment_date),
  CONSTRAINT fk_daily_judgment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
