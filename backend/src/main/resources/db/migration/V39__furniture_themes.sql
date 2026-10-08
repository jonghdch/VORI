SET NAMES utf8mb4;

-- 가구 테마 6종 추가(오션·캠핑·모던·프린세스·동굴·우주)와 가구별 테마 재배정.
-- FurnitureCatalog.themeName 과 짝 — 이름이 어긋나면 그 가구는 테마 없는 가구처럼 동작한다.
--
-- 새 테마는 상점에서 골라 보기 위한 분류다. set_bonus_pct 가 0 이라 세트가 발동해도
-- 배웅 선물은 그대로이고, unlock_title_id 가 NULL 이라 처음부터 누구나 살 수 있다.
-- required_count 는 그 테마의 가구 수를 넘지 않게 잡았다(프린세스·우주 2종, 동굴 1종).
INSERT INTO theme_master (name, set_bonus_pct, required_count, unlock_title_id) VALUES
  ('오션',     0.00, 3, NULL),
  ('캠핑',     0.00, 3, NULL),
  ('모던',     0.00, 3, NULL),
  ('프린세스', 0.00, 2, NULL),
  ('동굴',     0.00, 1, NULL),
  ('우주',     0.00, 2, NULL);

-- 이미 산 가구도 새 분류를 따르게 한다. user_furniture 는 구매 시 theme_id 를 복사해 두는
-- 반정규화 구조라, 카탈로그만 고치면 보유분은 옛 테마(대부분 NULL)로 남는다 — V18 과 같은 이유.
-- 가구는 category 로 고른다(이 가구들은 카테고리 하나에 한 종씩이다). 새 DB 에서는 0행이다.
--
-- 기존 테마로 들어가는 가구: 흔들의자·벽난로 → 코지, 빈 책상·책상 → 스터디.
-- 컴퓨터는 스터디에서 모던으로 옮긴다.
UPDATE user_furniture uf JOIN theme_master t ON t.name = '오션'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('BEACH_BALL', 'PARASOL', 'HAMMOCK', 'SWIM_TUBE');

UPDATE user_furniture uf JOIN theme_master t ON t.name = '캠핑'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('CAMP_CHAIR', 'LANTERN', 'CAMPFIRE', 'ICEBOX', 'TENT');

UPDATE user_furniture uf JOIN theme_master t ON t.name = '모던'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('LEATHER_SOFA', 'COMPUTER', 'GLASS_TABLE', 'SAFE');

UPDATE user_furniture uf JOIN theme_master t ON t.name = '스터디'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('EMPTY_DESK', 'DESK');

UPDATE user_furniture uf JOIN theme_master t ON t.name = '프린세스'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('TEA_TABLE', 'CANOPY_BED');

UPDATE user_furniture uf JOIN theme_master t ON t.name = '동굴'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('TREASURE_CHEST');

UPDATE user_furniture uf JOIN theme_master t ON t.name = '코지'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('ROCKING_CHAIR', 'FIREPLACE');

UPDATE user_furniture uf JOIN theme_master t ON t.name = '우주'
  SET uf.theme_id = t.id
  WHERE uf.category IN ('TELESCOPE', 'SLEEP_CAPSULE');
