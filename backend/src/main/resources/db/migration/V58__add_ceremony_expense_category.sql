-- 결혼식·장례식·돌잔치 등의 비정기 경조사는 생활(지구력) 그룹에서 기록한다.
-- 이미 생성된 사용자 DB에도 한 번만 추가한다.
INSERT INTO categories (parent_id, name, stat_type, sort_order)
SELECT parent.id, '경조사·축의금', 'ENDURANCE', 99
FROM categories parent
WHERE parent.parent_id IS NULL
  AND parent.name = '생활'
  AND NOT EXISTS (
    SELECT 1
    FROM categories child
    WHERE child.parent_id = parent.id
      AND child.name = '경조사·축의금'
  );
