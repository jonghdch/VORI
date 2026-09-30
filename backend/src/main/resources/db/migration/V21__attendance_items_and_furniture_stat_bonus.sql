CREATE TABLE attendance_checkins (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  checked_in_date DATE NOT NULL,
  item_awarded BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_attendance_user_date UNIQUE (user_id, checked_in_date),
  CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE user_stat_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  stat_type ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  stat_delta INT NOT NULL,
  acquired_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_user_stat_items_user FOREIGN KEY (user_id) REFERENCES users(id)
);

ALTER TABLE pet_growth_logs
  MODIFY COLUMN reason ENUM('EXPENSE_SAVING','GOAL_ACHIEVED','BONUS','DAILY_JUDGMENT','ATTENDANCE_ITEM','FURNITURE_BONUS') NOT NULL;
