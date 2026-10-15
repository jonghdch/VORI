-- 상점에서 뺀 "기본 침대"(옛 FurnitureCatalog.BASIC_BED)를 이미 산 사용자에게서도 없앤다.
-- user_furniture 는 구매 시 이름을 복사해 두는 반정규화 구조라, 카탈로그에서 빼도 보유분은 남는다.
-- 마이룸에 배치해 둔 것도 함께 사라진다. 코인은 돌려주지 않는다.
-- 새 DB 에서는 0행이다.
DELETE FROM user_furniture WHERE category = 'BED' AND name = '기본 침대';
