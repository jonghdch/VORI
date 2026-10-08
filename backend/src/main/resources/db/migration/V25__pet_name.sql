-- 펫 이름. 사용자가 직접 지어 준다(1~10자).
--
-- NULL = 아직 이름을 짓지 않은 펫. 가입 때 받는 시작 펫과 이 마이그레이션 이전에 생긴 펫이
-- 여기에 해당하고, 화면이 이름 짓기 팝업을 띄워 채운다. 이미 분양한 펫은 NULL 로 남는다.
ALTER TABLE pets
  ADD COLUMN name VARCHAR(10) NULL AFTER species_id;
