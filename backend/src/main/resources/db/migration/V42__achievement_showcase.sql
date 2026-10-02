SET NAMES utf8mb4;

-- 내 정보 상자에 올려 보여 줄 업적(최대 3개). 1~3 = 보여 줄 순서, NULL = 올리지 않음.
-- 헤더에 칭호 배지를 두지 않는 대신, 유저가 고른 업적을 내 정보 상자에서 보여 준다.
ALTER TABLE user_titles
  ADD COLUMN showcase_order TINYINT NULL AFTER acquired_at,
  ADD UNIQUE KEY uq_user_titles_showcase (user_id, showcase_order);
