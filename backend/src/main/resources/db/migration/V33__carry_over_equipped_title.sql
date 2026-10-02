SET NAMES utf8mb4;

-- 옛 칭호 장착(users.active_title_id → user_titles.id)을 업적 장착 첫 칸(equip_order = 1)으로 옮긴다.
-- 장착 기능이 업적 3칸으로 바뀌면서 배포 순간 사용자가 장착해 둔 것이 사라지지 않게.
-- 이미 첫 칸이 차 있는 사용자는 건드리지 않는다(UNIQUE(user_id, equip_order)).
UPDATE user_titles ut
JOIN users u ON u.active_title_id = ut.id AND u.id = ut.user_id
SET ut.equip_order = 1
WHERE ut.equip_order IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT user_id FROM user_titles WHERE equip_order = 1) taken
    WHERE taken.user_id = ut.user_id
  );
