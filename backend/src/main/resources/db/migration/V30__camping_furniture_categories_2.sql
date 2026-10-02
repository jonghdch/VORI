-- 캠핑 가구 3종 추가(해먹·파라솔·캠핑 의자) — user_furniture.category 에 값을 더 붙인다.
-- FurnitureCategory enum · UserFurniture.columnDefinition 과 짝. V29 를 고치지 않고 새 버전으로 둔다
-- (이미 V29 가 적용된 DB 에서 체크섬이 어긋나지 않도록). 기존 값의 순서는 그대로, 뒤에만 붙인다.
ALTER TABLE user_furniture
  MODIFY COLUMN category ENUM(
    'BED','WALLPAPER','FLOOR','MIRROR','VANITY','PICTURE','BOARD','SHELF','DRAWER','COMPUTER',
    'TENT','PICNIC_MAT','CAMPFIRE',
    'HAMMOCK','PARASOL','CAMP_CHAIR'
  ) NOT NULL;
