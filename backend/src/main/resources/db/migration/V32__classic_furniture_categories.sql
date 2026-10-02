-- 클래식 가구 3종 추가(캐노피 침대·티 테이블·벽난로) — user_furniture.category 에 값을 더 붙인다.
-- FurnitureCategory enum · UserFurniture.columnDefinition 과 짝. 기존 값의 순서는 그대로, 뒤에만 붙인다.
ALTER TABLE user_furniture
  MODIFY COLUMN category ENUM(
    'BED','WALLPAPER','FLOOR','MIRROR','VANITY','PICTURE','BOARD','SHELF','DRAWER','COMPUTER',
    'TENT','PICNIC_MAT','CAMPFIRE',
    'HAMMOCK','PARASOL','CAMP_CHAIR',
    'CANOPY_BED','TEA_TABLE','FIREPLACE'
  ) NOT NULL;
