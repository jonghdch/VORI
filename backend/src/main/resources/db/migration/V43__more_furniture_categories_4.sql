-- 가구 2종 추가(책상·냉장고) — user_furniture.category 에 값을 더 붙인다.
-- FurnitureCategory enum · UserFurniture.columnDefinition 과 짝. 기존 값의 순서는 그대로, 뒤에만 붙인다.
ALTER TABLE user_furniture
  MODIFY COLUMN category ENUM(
    'BED','WALLPAPER','FLOOR','MIRROR','VANITY','PICTURE','BOARD','SHELF','DRAWER','COMPUTER',
    'TENT','PICNIC_MAT','CAMPFIRE',
    'HAMMOCK','PARASOL','CAMP_CHAIR',
    'CANOPY_BED','TEA_TABLE','FIREPLACE',
    'ROCKING_CHAIR','TREASURE_CHEST','SLEEP_CAPSULE',
    'TELESCOPE','SWIM_TUBE','BEACH_BALL',
    'LEATHER_SOFA','GLASS_TABLE',
    'LANTERN','ICEBOX','SAFE',
    'DESK','FRIDGE'
  ) NOT NULL;
