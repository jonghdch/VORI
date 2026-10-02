SET NAMES utf8mb4;

-- 화면 용어("장착")에 맞춰 컬럼 이름을 통일한다. V30·V31 은 이미 적용된 곳이 있어 고치지 않고 여기서 바꾼다.
--   pets.featured_title_award_id → equipped_title_award_id  (펫이 장착한 칭호)
--   user_titles.showcase_order   → equip_order              (장착한 업적 순서 1~3)

ALTER TABLE pets
  DROP FOREIGN KEY fk_pets_featured_title_award;
ALTER TABLE pets
  RENAME COLUMN featured_title_award_id TO equipped_title_award_id,
  ADD CONSTRAINT fk_pets_equipped_title_award
    FOREIGN KEY (equipped_title_award_id) REFERENCES pet_title_awards(id) ON DELETE SET NULL;

ALTER TABLE user_titles
  DROP INDEX uq_user_titles_showcase,
  RENAME COLUMN showcase_order TO equip_order,
  ADD UNIQUE KEY uq_user_titles_equip (user_id, equip_order);
