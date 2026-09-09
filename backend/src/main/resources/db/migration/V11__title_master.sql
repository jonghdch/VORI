SET NAMES utf8mb4;

CREATE TABLE titles (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(50) NOT NULL,
  name          VARCHAR(50) NOT NULL,
  description   VARCHAR(200) NOT NULL,
  metric_type   ENUM('TOTAL_SAVED','EXPENSE_COUNT','GOALS_ACHIEVED','PETS_RELEASED','S_TIER_PETS','AI_ANSWERS','RECEIPT_SCANS') NOT NULL,
  threshold     BIGINT NOT NULL,
  enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order    INT NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_titles_code (code),
  INDEX idx_titles_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO titles (code, name, description, metric_type, threshold, enabled, sort_order, created_at, updated_at) VALUES
  ('SAVER_SPROUT', '절약 새싹', '누적 절약 10만원', 'TOTAL_SAVED', 100000, TRUE, 10, NOW(), NOW()),
  ('SAVER_MID', '절약 중수', '누적 절약 50만원', 'TOTAL_SAVED', 500000, TRUE, 20, NOW(), NOW()),
  ('SAVER_MASTER', '절약 고수', '누적 절약 100만원', 'TOTAL_SAVED', 1000000, TRUE, 30, NOW(), NOW()),
  ('RECORD_START', '기록의 시작', '지출 10건 기록', 'EXPENSE_COUNT', 10, TRUE, 40, NOW(), NOW()),
  ('RECORD_STEADY', '꾸준한 기록가', '지출 50건 기록', 'EXPENSE_COUNT', 50, TRUE, 50, NOW(), NOW()),
  ('RECORD_MASTER', '기록 마스터', '지출 100건 기록', 'EXPENSE_COUNT', 100, TRUE, 60, NOW(), NOW()),
  ('GOAL_FIRST', '목표 달성자', '절약 목표 1회 달성', 'GOALS_ACHIEVED', 1, TRUE, 70, NOW(), NOW()),
  ('GOAL_PLANNER', '계획적인 소비자', '절약 목표 5회 달성', 'GOALS_ACHIEVED', 5, TRUE, 80, NOW(), NOW()),
  ('PET_FIRST_RELEASE', '첫 분양', '펫 1마리 분양', 'PETS_RELEASED', 1, TRUE, 90, NOW(), NOW()),
  ('PET_COLLECTOR', '펫 컬렉터', '펫 5마리 분양', 'PETS_RELEASED', 5, TRUE, 100, NOW(), NOW()),
  ('LUCKY', '행운아', 'S등급 펫 획득', 'S_TIER_PETS', 1, TRUE, 110, NOW(), NOW()),
  ('TALKATIVE', '소통왕', 'AI 질문 10회 답변', 'AI_ANSWERS', 10, TRUE, 120, NOW(), NOW()),
  ('SCAN_MASTER', '스캔 마스터', '영수증 10회 인식', 'RECEIPT_SCANS', 10, TRUE, 130, NOW(), NOW());

ALTER TABLE user_titles
  ADD COLUMN title_id BIGINT NULL AFTER user_id;

UPDATE user_titles ut
JOIN titles t ON t.name = ut.name
SET ut.title_id = t.id
WHERE ut.title_id IS NULL;

INSERT INTO titles (code, name, description, metric_type, threshold, enabled, sort_order, created_at, updated_at)
SELECT
  CONCAT('LEGACY_', MIN(ut.id)),
  ut.name,
  CONCAT('이전 버전에서 이관된 칭호: ', ut.name),
  'EXPENSE_COUNT',
  9223372036854775807,
  FALSE,
  100000,
  NOW(),
  NOW()
FROM user_titles ut
LEFT JOIN titles t ON t.name = ut.name
WHERE ut.title_id IS NULL
  AND t.id IS NULL
GROUP BY ut.name;

UPDATE user_titles ut
JOIN titles t ON t.name = ut.name
SET ut.title_id = t.id
WHERE ut.title_id IS NULL;

ALTER TABLE user_titles
  DROP INDEX uq_user_titles_user_name,
  MODIFY COLUMN title_id BIGINT NOT NULL,
  ADD UNIQUE KEY uq_user_titles_user_title (user_id, title_id),
  ADD CONSTRAINT fk_user_titles_title
    FOREIGN KEY (title_id) REFERENCES titles(id)
    ON DELETE RESTRICT,
  DROP COLUMN name;
