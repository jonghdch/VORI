-- 마이룸 가구 테마 시드.
--
-- 세트는 마이룸에 **배치된** 가구만 센다(user_furniture.position_x/y). 그래서 벽지·바닥은
-- 어느 테마에도 넣지 않았다 — 좌표 처리가 보류라 배치될 수 없고, 테마에 넣으면 그 세트는
-- 영원히 발동하지 않는다. 벽지·바닥 배치가 정해지면 그때 테마를 붙인다.
--
-- id 를 고정하지 않는다. 이름이 UNIQUE 라 코드는 이름으로 찾는다(FurnitureCatalog.themeName).
-- required_count 는 테마마다 다르다 — 스터디는 짝이 마땅치 않은 컴퓨터를 살리려고 2개다.
INSERT INTO theme_master (name, set_bonus_pct, required_count, unlock_title_name) VALUES
  ('우드',    8.00, 3, NULL),
  ('코지',   12.00, 3, '절약 새싹'),
  ('스터디', 15.00, 2, '기록의 시작');
