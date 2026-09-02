-- 신규 가입자에게 자동 지급할 시작 펫 지정.
--
-- PetSpeciesSeeder 는 pet_species 가 비어 있을 때만 INSERT 하므로,
-- 이미 시드된 DB(팀원 로컬 포함)에는 시더 코드를 고쳐도 반영되지 않는다. 그래서 마이그레이션으로 처리.
--
-- 강아지를 고른 이유: 설계서의 마스코트 '보리'가 강아지이고,
-- 프론트에 존재하는 유일한 펫 이미지도 bori.png 다.
-- B 등급은 그대로 두어 가챠 추첨 대상에서는 빠지지 않는다.
UPDATE pet_species SET is_starter = TRUE WHERE name = '강아지';
