-- 캠핑 가구 3종(텐트·피크닉 매트·모닥불)을 살 수 있도록 user_furniture.category 에 값을 추가한다.
-- FurnitureCategory enum · UserFurniture.columnDefinition 과 짝. 기존 값의 순서는 그대로 두고 뒤에만 붙인다.
ALTER TABLE user_furniture
  MODIFY COLUMN category ENUM(
    'BED','WALLPAPER','FLOOR','MIRROR','VANITY','PICTURE','BOARD','SHELF','DRAWER','COMPUTER',
    'TENT','PICNIC_MAT','CAMPFIRE'
  ) NOT NULL;
