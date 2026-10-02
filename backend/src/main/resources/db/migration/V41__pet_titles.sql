SET NAMES utf8mb4;

-- 업적·칭호 분리 — 업적은 유저(titles·user_titles, 지금까지의 "칭호"), 칭호는 펫에게 붙는다.
-- 펫 칭호는 그 펫과 지내는 동안의 기록으로만 판정한다. 펫을 분양하고 새 펫을 키우면 과제는 0부터 다시 시작하고,
-- 분양한 펫이 딴 칭호는 그 펫의 기록으로 남아 도감에서 보인다.

-- 펫 칭호 마스터. metric_type 은 PetTitleMetricType 과 짝.
CREATE TABLE pet_titles (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(50)  NOT NULL,
  name          VARCHAR(50)  NOT NULL,
  description   VARCHAR(200) NOT NULL,
  metric_type   ENUM('LEVEL','INTERACTIONS','AI_ANSWERS','CHARM_BONUS') NOT NULL,
  threshold     BIGINT       NOT NULL,
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
  hidden        BOOLEAN      NOT NULL DEFAULT FALSE,  -- 따기 전에는 칭호 목록에 내려가지 않는다
  sort_order    INT          NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_pet_titles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 펫별 획득 기록. 한 번 딴 칭호는 회수하지 않는다.
CREATE TABLE pet_title_awards (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  pet_id            BIGINT   NOT NULL,
  pet_title_id      BIGINT   NOT NULL,
  unlock_condition  JSON     NULL,      -- 획득 시점 근거 {code, threshold, value}
  acquired_at       DATETIME NOT NULL,
  UNIQUE KEY uq_pet_title_awards_pet_title (pet_id, pet_title_id),
  INDEX idx_pet_title_awards_title (pet_title_id),
  CONSTRAINT fk_pet_title_awards_pet
    FOREIGN KEY (pet_id) REFERENCES pets(id) ON DELETE CASCADE,
  CONSTRAINT fk_pet_title_awards_title
    FOREIGN KEY (pet_title_id) REFERENCES pet_titles(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 대표 칭호 — 펫이 딴 칭호 중 유저가 골라 헤더·홈에 보여 주는 1개. NULL = 고르지 않음.
ALTER TABLE pets
  ADD COLUMN featured_title_award_id BIGINT NULL AFTER interaction_count,
  ADD CONSTRAINT fk_pets_featured_title_award
    FOREIGN KEY (featured_title_award_id) REFERENCES pet_title_awards(id) ON DELETE SET NULL;

-- AI 답변 수 칭호(펫 수명 구간 안의 답변)를 셀 때 쓴다.
CREATE INDEX idx_ai_inquiries_user_answered ON ai_inquiries (user_id, answered_at);

INSERT INTO pet_titles (code, name, description, metric_type, threshold, enabled, hidden, sort_order) VALUES
  ('PET_EVOLVE_2',    '첫 진화',     '2차로 진화 (Lv. 5)',              'LEVEL',        5,   TRUE, FALSE, 10),
  ('PET_EVOLVE_3',    '어엿한 어른', '3차로 진화 (Lv. 15)',             'LEVEL',        15,  TRUE, FALSE, 20),
  ('PET_LOVELY',      '사랑둥이',    '이 펫과 상호작용 100회',          'INTERACTIONS', 100, TRUE, FALSE, 30),
  ('PET_TALKATIVE',   '수다쟁이',    '이 펫과 지내는 동안 AI 질문에 100회 답변', 'AI_ANSWERS', 100, TRUE, FALSE, 40),
  ('PET_LUCKY_CHARM', '행운의 매력', '상호작용 매력 보너스 10회',       'CHARM_BONUS',  10,  TRUE, TRUE,  50);

-- 히든 칭호였던 「사랑둥이」는 펫 칭호로 옮겼다. 업적 쪽은 끄고, 이미 받은 기록은 남긴다.
UPDATE titles SET enabled = FALSE WHERE code = 'PET_LOVELY';

-- 업적 지표 추가 (TitleMetricType 과 짝).
ALTER TABLE titles
  MODIFY COLUMN metric_type ENUM(
    'TOTAL_SAVED','EXPENSE_COUNT','GOALS_ACHIEVED','PETS_RELEASED',
    'S_TIER_PETS','AI_ANSWERS','RECEIPT_SCANS','LOGIN_COUNT','PET_INTERACTIONS',
    'PETS_HATCHED','SPECIES_GRADUATED','PET_TITLES_TOTAL','PET_TITLE_KINDS',
    'PET_TITLES_ON_ONE_PET','HIDDEN_PET_TITLES'
  ) NOT NULL;

-- 키운 펫 수·펫 칭호 수 업적. sort_order 는 펫 업적(90·100) 사이와 끝(140~)에 둔다.
INSERT INTO titles (code, name, description, metric_type, threshold, enabled, hidden, sort_order, created_at, updated_at) VALUES
  ('PET_NEW_FAMILY',   '새 식구',       '펫 3마리 부화 (시작 펫 포함)',        'PETS_HATCHED',          3,  TRUE, FALSE, 92,  NOW(), NOW()),
  ('PET_VETERAN',      '베테랑 사육사', '펫 10마리 분양',                      'PETS_RELEASED',         10, TRUE, FALSE, 102, NOW(), NOW()),
  ('DEX_COLLECTOR',    '도감 수집가',   '서로 다른 종 5종 졸업',               'SPECIES_GRADUATED',     5,  TRUE, FALSE, 103, NOW(), NOW()),
  ('DEX_COMPLETE',     '도감 완성',     '서로 다른 종 16종 모두 졸업',         'SPECIES_GRADUATED',     16, TRUE, FALSE, 104, NOW(), NOW()),
  ('PET_TITLE_FIRST',  '첫 칭호',       '펫이 칭호를 처음 얻음',               'PET_TITLES_TOTAL',      1,  TRUE, FALSE, 140, NOW(), NOW()),
  ('PET_TITLE_10',     '칭호 수집가',   '펫들이 얻은 칭호 합계 10개',          'PET_TITLES_TOTAL',      10, TRUE, FALSE, 141, NOW(), NOW()),
  ('PET_TITLE_30',     '칭호 장인',     '펫들이 얻은 칭호 합계 30개',          'PET_TITLES_TOTAL',      30, TRUE, FALSE, 142, NOW(), NOW()),
  ('PET_TITLE_KINDS',  '칭호 도감',     '공개 펫 칭호 4종을 모두 한 번씩 얻음', 'PET_TITLE_KINDS',       4,  TRUE, FALSE, 143, NOW(), NOW()),
  ('PET_ALLROUNDER',   '만능 펫',       '한 펫이 칭호 5개를 모두 얻음',        'PET_TITLES_ON_ONE_PET', 5,  TRUE, FALSE, 144, NOW(), NOW()),
  ('PET_SECRET_FOUND', '비밀 발견',     '히든 펫 칭호를 처음 얻음',            'HIDDEN_PET_TITLES',     1,  TRUE, TRUE,  145, NOW(), NOW());
