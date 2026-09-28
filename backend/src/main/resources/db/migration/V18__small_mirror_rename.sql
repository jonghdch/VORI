-- 가구 "작은 거울" 의 이름을 "거울" 로 바꾼다 (FurnitureCatalog.SMALL_MIRROR 표시 이름 변경).
-- user_furniture 는 구매 시 이름을 복사해 두는 반정규화 구조라, 이미 산 가구는 여기서 함께 고친다.
-- 새 DB 에서는 0행이다.
UPDATE user_furniture SET name = '거울' WHERE category = 'MIRROR' AND name = '작은 거울';
