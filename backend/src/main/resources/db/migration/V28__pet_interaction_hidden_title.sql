SET NAMES utf8mb4;

-- 펫 상호작용 횟수 — 쓰다듬기·칭찬하기 등 1회마다 1씩 오른다(PetService.interact).
-- 히든 칭호 「사랑둥이」 조건을 판정하려면 상호작용을 펫별로 세어야 한다.
ALTER TABLE pets
  ADD COLUMN interaction_count INT NOT NULL DEFAULT 0 AFTER stat_endurance;

-- titles.metric_type 에 PET_INTERACTIONS 추가 (TitleMetricType 과 짝).
ALTER TABLE titles
  MODIFY COLUMN metric_type ENUM(
    'TOTAL_SAVED','EXPENSE_COUNT','GOALS_ACHIEVED','PETS_RELEASED',
    'S_TIER_PETS','AI_ANSWERS','RECEIPT_SCANS','LOGIN_COUNT','PET_INTERACTIONS'
  ) NOT NULL;

-- 히든 칭호 — 따기 전에는 칭호 목록에 내려가지 않는다. 기존 칭호는 전부 공개(FALSE).
ALTER TABLE titles
  ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE AFTER enabled;

-- 신규 히든 칭호 「사랑둥이」 — 한 펫과 상호작용 100회.
-- sort_order 105 — 펫 칭호(90·100) 뒤, 가챠(110) 앞.
INSERT INTO titles (code, name, description, metric_type, threshold, enabled, hidden, sort_order, created_at, updated_at) VALUES
  ('PET_LOVELY', '사랑둥이', '펫과 상호작용 100회', 'PET_INTERACTIONS', 100, TRUE, TRUE, 105, NOW(), NOW());
