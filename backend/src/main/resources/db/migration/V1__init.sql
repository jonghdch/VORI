-- VORI 초기 스키마 (테이블 18종)
-- 명세: ~/Documents/Obsidian Vault/Projects/vori/notes/db-table-spec.md
--
-- 의존성 순서:
--   1) FK 없거나 자기참조만:    users(예외), categories, pet_species, theme_master
--   2) users 만 참조:           goals, monthly_budgets, incomes, user_stat_stats, eggs, user_titles, user_furniture
--   3) 깊이 2~:                expenses, ai_inquiries, receipt_ocr_jobs, daily_reports, pets, gacha_pulls, pet_growth_logs
--   4) ALTER users:            active_title_id FK 추가 (users ↔ user_titles 순환 해결)

SET NAMES utf8mb4;

-- ============================================================
-- 1. users (active_title_id FK 는 user_titles 생성 후 ALTER 로 추가)
-- ============================================================
CREATE TABLE users (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  email                 VARCHAR(100) NOT NULL,
  password_hash         VARCHAR(255) NOT NULL,
  nickname              VARCHAR(30) NOT NULL,
  name                  VARCHAR(30),
  age                   TINYINT UNSIGNED,
  job                   VARCHAR(50),
  monthly_income        INT UNSIGNED,
  active_title_id       BIGINT NULL,
  total_saved           INT DEFAULT 0,
  game_money            INT DEFAULT 0,
  tutorial_done         BOOLEAN DEFAULT FALSE,
  role                  ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  terms_agreed_at       DATETIME NOT NULL,
  privacy_agreed_at     DATETIME NOT NULL,
  marketing_agreed_at   DATETIME NULL,
  created_at            DATETIME NOT NULL,
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 4. categories (자기참조)
-- ============================================================
CREATE TABLE categories (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id    BIGINT NULL,
  name         VARCHAR(30) NOT NULL,
  stat_type    ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  sort_order   INT DEFAULT 0,
  is_active    BOOLEAN DEFAULT TRUE,
  UNIQUE KEY uq_categories_parent_name (parent_id, name),
  CONSTRAINT fk_categories_parent
    FOREIGN KEY (parent_id) REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 11. pet_species
-- ============================================================
CREATE TABLE pet_species (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(30) NOT NULL,
  tier            ENUM('STARTER','S','A','B','C') NOT NULL,
  is_starter      BOOLEAN DEFAULT FALSE,
  appearance_key  VARCHAR(50) NOT NULL,
  UNIQUE KEY uq_pet_species_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 16. theme_master
-- ============================================================
CREATE TABLE theme_master (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  name                VARCHAR(50) NOT NULL,
  set_bonus_pct       DECIMAL(5,2) DEFAULT 0,
  required_count      TINYINT DEFAULT 3,
  unlock_title_name   VARCHAR(50) NULL,
  UNIQUE KEY uq_theme_master_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 2. goals
-- ============================================================
CREATE TABLE goals (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  category_id     BIGINT NULL,
  `year_month`      CHAR(7) NOT NULL,
  target_amount   INT UNSIGNED NOT NULL,
  current_amount  INT UNSIGNED DEFAULT 0,
  status          ENUM('ACTIVE','DONE','ABANDONED') NOT NULL DEFAULT 'ACTIVE',
  INDEX idx_goals_user_month (user_id, `year_month`),
  INDEX idx_goals_user_category (user_id, category_id),
  UNIQUE KEY uq_goals_user_month_category (user_id, `year_month`, category_id),
  CONSTRAINT fk_goals_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_goals_category
    FOREIGN KEY (category_id) REFERENCES categories(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 3. monthly_budgets
-- ============================================================
CREATE TABLE monthly_budgets (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  `year_month`  CHAR(7) NOT NULL,
  amount      INT UNSIGNED NOT NULL,
  UNIQUE KEY uq_monthly_budgets_user_month (user_id, `year_month`),
  CONSTRAINT fk_monthly_budgets_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 5. expenses
-- ============================================================
CREATE TABLE expenses (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  spent_at        DATETIME NOT NULL,
  time_provided   BOOLEAN NOT NULL DEFAULT FALSE,
  amount          INT UNSIGNED NOT NULL,
  item            VARCHAR(100) NOT NULL,
  category_id     BIGINT NOT NULL,
  stat_type       ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  z_score         DECIMAL(6,3),
  signal_initial  ENUM('RED','GRAY','GREEN'),
  signal_final    ENUM('RED','GRAY','GREEN'),
  stat_delta      INT UNSIGNED DEFAULT 0,
  saved_amount    INT,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_expenses_user_time     (user_id, spent_at),
  INDEX idx_expenses_user_category (user_id, category_id),
  INDEX idx_expenses_user_stat     (user_id, stat_type, spent_at),
  CONSTRAINT fk_expenses_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_expenses_category
    FOREIGN KEY (category_id) REFERENCES categories(id)
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 6. incomes
-- ============================================================
CREATE TABLE incomes (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT NOT NULL,
  received_at  DATE NOT NULL,
  amount       INT UNSIGNED NOT NULL,
  source       ENUM('ALLOWANCE','PART_TIME','SCHOLARSHIP','SIDE_JOB','GIFT','INTEREST','OTHER') NOT NULL,
  note         VARCHAR(200) NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_incomes_user_date (user_id, received_at),
  CONSTRAINT fk_incomes_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 7. user_stat_stats
-- ============================================================
CREATE TABLE user_stat_stats (
  user_id       BIGINT NOT NULL,
  stat_type     ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  mean_ema      DECIMAL(12,2) NOT NULL,
  stddev_ema    DECIMAL(12,2) NOT NULL,
  sample_count  INT NOT NULL DEFAULT 0,
  updated_at    DATETIME NOT NULL
                DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, stat_type),
  CONSTRAINT fk_user_stat_stats_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 8. ai_inquiries
-- ============================================================
CREATE TABLE ai_inquiries (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  expense_id        BIGINT NOT NULL,
  user_id           BIGINT NOT NULL,
  question          TEXT NOT NULL,
  answer_text       TEXT NULL,
  reason_category   ENUM('CEREMONY','EMERGENCY','SOCIAL','SELF_INVEST','IMPULSE','ETC') NULL,
  signal_adjusted   BOOLEAN DEFAULT FALSE,
  asked_at          DATETIME NOT NULL,
  answered_at       DATETIME NULL,
  UNIQUE KEY uq_ai_inquiries_expense (expense_id),
  INDEX idx_ai_inquiries_user_reason (user_id, reason_category, answered_at),
  CONSTRAINT fk_ai_inquiries_expense
    FOREIGN KEY (expense_id) REFERENCES expenses(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_ai_inquiries_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 9. receipt_ocr_jobs
-- ============================================================
CREATE TABLE receipt_ocr_jobs (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  expense_id        BIGINT NULL,
  user_id           BIGINT NOT NULL,
  receipt_path      VARCHAR(255) NOT NULL,
  provider          ENUM('GOOGLE_VISION') NOT NULL DEFAULT 'GOOGLE_VISION',
  status            ENUM('PENDING','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
  extracted_text    TEXT NULL,
  extracted_amount  INT UNSIGNED NULL,
  extracted_date    DATE NULL,
  extracted_item    VARCHAR(100) NULL,
  error_message     TEXT NULL,
  requested_at      DATETIME NOT NULL,
  completed_at      DATETIME NULL,
  INDEX idx_receipt_ocr_user_time (user_id, requested_at),
  INDEX idx_receipt_ocr_expense   (expense_id),
  CONSTRAINT fk_receipt_ocr_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_receipt_ocr_expense
    FOREIGN KEY (expense_id) REFERENCES expenses(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 10. daily_reports
-- ============================================================
CREATE TABLE daily_reports (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  report_date       DATE NOT NULL,
  income_total      INT UNSIGNED NOT NULL,
  expense_total     INT UNSIGNED NOT NULL,
  saved_amount      INT NOT NULL,
  stat_delta_total  INT,
  pet_snapshot      JSON,
  ai_comment        TEXT,
  generated_at      DATETIME NOT NULL,
  read_at           DATETIME NULL,
  UNIQUE KEY uq_daily_reports_user_date (user_id, report_date),
  CONSTRAINT fk_daily_reports_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 13. eggs (pets 보다 먼저 — pets.egg_id 가 eggs 참조)
-- ============================================================
CREATE TABLE eggs (
  id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id                   BIGINT NOT NULL,
  grade_name                VARCHAR(20) NOT NULL,
  price_game_money          INT UNSIGNED NOT NULL,
  probability_distribution  JSON NOT NULL,
  purchased_at              DATETIME NOT NULL,
  opened_at                 DATETIME NULL,
  CONSTRAINT fk_eggs_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 12. pets
-- ============================================================
CREATE TABLE pets (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  species_id      BIGINT NOT NULL,
  egg_id          BIGINT NULL,
  hatched_at      DATETIME NULL,
  stat_energy     INT DEFAULT 0,
  stat_charm      INT DEFAULT 0,
  stat_iq         INT DEFAULT 0,
  stat_endurance  INT DEFAULT 0,
  stage           ENUM('INFANT','JUVENILE','ADULT') DEFAULT 'INFANT',
  variant         ENUM('NORMAL','IRO','ALIEN') DEFAULT 'NORMAL',
  created_at      DATETIME NOT NULL,
  released_at     DATETIME NULL,
  release_value   INT UNSIGNED NULL,
  UNIQUE KEY uq_pets_egg (egg_id),
  INDEX idx_pets_user_released (user_id, released_at),
  CONSTRAINT fk_pets_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_pets_species
    FOREIGN KEY (species_id) REFERENCES pet_species(id)
    ON DELETE RESTRICT,
  CONSTRAINT fk_pets_egg
    FOREIGN KEY (egg_id) REFERENCES eggs(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 14. gacha_pulls
-- ============================================================
CREATE TABLE gacha_pulls (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  egg_id            BIGINT NOT NULL,
  drawn_species_id  BIGINT NOT NULL,
  variant           ENUM('NORMAL','IRO','ALIEN') NOT NULL,
  drawn_at          DATETIME NOT NULL,
  UNIQUE KEY uq_gacha_pulls_egg (egg_id),
  CONSTRAINT fk_gacha_pulls_egg
    FOREIGN KEY (egg_id) REFERENCES eggs(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_gacha_pulls_species
    FOREIGN KEY (drawn_species_id) REFERENCES pet_species(id)
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 15. pet_growth_logs
-- ============================================================
CREATE TABLE pet_growth_logs (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  pet_id        BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  expense_id    BIGINT NULL,
  goal_id       BIGINT NULL,
  stat_type     ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  delta         INT NOT NULL,
  saved_amount  INT NOT NULL,
  reason        ENUM('EXPENSE_SAVING','GOAL_ACHIEVED','BONUS') NOT NULL,
  created_at    DATETIME NOT NULL,
  INDEX idx_pet_growth_pet  (pet_id),
  INDEX idx_pet_growth_user (user_id),
  CONSTRAINT fk_pet_growth_pet
    FOREIGN KEY (pet_id) REFERENCES pets(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_pet_growth_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_pet_growth_expense
    FOREIGN KEY (expense_id) REFERENCES expenses(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_pet_growth_goal
    FOREIGN KEY (goal_id) REFERENCES goals(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 17. user_furniture
-- ============================================================
CREATE TABLE user_furniture (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id             BIGINT NOT NULL,
  name                VARCHAR(50) NOT NULL,
  category            ENUM('BED','WALLPAPER','FLOOR','MIRROR','VANITY','PICTURE','BOARD','SHELF','DRAWER','COMPUTER') NOT NULL,
  stat_target         ENUM('ENERGY','CHARM','IQ','ENDURANCE') NOT NULL,
  release_bonus_pct   DECIMAL(5,2) DEFAULT 0,
  theme_id            BIGINT NULL,
  price_game_money    INT UNSIGNED NOT NULL,
  position_x          SMALLINT NULL,
  position_y          SMALLINT NULL,
  acquired_at         DATETIME NOT NULL,
  CONSTRAINT fk_user_furniture_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_user_furniture_theme
    FOREIGN KEY (theme_id) REFERENCES theme_master(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 18. user_titles
-- ============================================================
CREATE TABLE user_titles (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT NOT NULL,
  name               VARCHAR(50) NOT NULL,
  unlock_condition   JSON NULL,
  unlocks_theme_id   BIGINT NULL,
  acquired_at        DATETIME NOT NULL,
  UNIQUE KEY uq_user_titles_user_name (user_id, name),
  CONSTRAINT fk_user_titles_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_user_titles_theme
    FOREIGN KEY (unlocks_theme_id) REFERENCES theme_master(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- users ↔ user_titles 순환 FK 마무리
-- ============================================================
ALTER TABLE users
  ADD CONSTRAINT fk_users_active_title
    FOREIGN KEY (active_title_id) REFERENCES user_titles(id)
    ON DELETE SET NULL;
