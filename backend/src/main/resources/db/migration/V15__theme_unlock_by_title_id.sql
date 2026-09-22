-- 테마 해금 조건을 칭호 "표시 이름"(unlock_title_name) 대신 titles.id FK 로 연결한다.
--
-- 이름으로 잇던 구조는 칭호 이름을 한 글자만 고쳐도 해금이 조용히 끊긴다(V11 로 칭호가
-- DB 마스터가 되면서 이름은 언제든 바뀔 수 있는 값이 됐다). id 로 잇고 FK 를 걸어
-- 존재하지 않는 칭호를 가리키는 테마가 생길 수 없게 한다.
--
-- 순서: 컬럼 추가 → 이름으로 백필 → FK → 옛 컬럼 제거. 백필 후 값이 비는 행이 있으면
-- 시드 이름이 어긋난 것이므로 마이그레이션을 실패시켜 드러낸다.
ALTER TABLE theme_master
  ADD COLUMN unlock_title_id BIGINT NULL AFTER unlock_title_name;

UPDATE theme_master tm
JOIN titles t ON t.name = tm.unlock_title_name
SET tm.unlock_title_id = t.id
WHERE tm.unlock_title_name IS NOT NULL;

-- 이름은 있는데 매칭되는 칭호가 없으면 여기서 멈춘다 (SIGNAL 로 명시적 실패).
SET @unmatched = (SELECT COUNT(*) FROM theme_master
                  WHERE unlock_title_name IS NOT NULL AND unlock_title_id IS NULL);
SET @msg = CONCAT('theme_master.unlock_title_name 과 일치하는 titles.name 이 없는 행: ', @unmatched);
-- MySQL 은 프로시저 밖에서 SIGNAL 을 쓸 수 없어, 0 나누기로 실패를 강제한다.
SELECT IF(@unmatched = 0, 0, 1 / 0) AS unmatched_guard;

ALTER TABLE theme_master
  ADD CONSTRAINT fk_theme_master_unlock_title
    FOREIGN KEY (unlock_title_id) REFERENCES titles(id)
    ON DELETE SET NULL,
  DROP COLUMN unlock_title_name;
