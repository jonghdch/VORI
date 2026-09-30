-- 펫 도감 개편 (16종 유지).
--   일반(C): 고양이·강아지·토끼·거북이
--   희귀(B): 사슴·여우·양·원숭이·다람쥐
--   에픽(A): 판다·너구리·펭귄·호랑이
--   레전드(S): 용·늑대·뱀
--
-- PetSpeciesSeeder 는 pet_species 가 비어 있을 때만 INSERT 하므로
-- 이미 시드된 DB 는 이 마이그레이션으로 맞춘다. 새 DB 에서는 모든 UPDATE 가 0행이고 시더가 새 목록을 넣는다.
--
-- 빠지는 종(개구리·사자)은 DELETE 하지 않고 새 종(너구리·호랑이)으로 행을 바꾼다.
-- pets·gacha_pulls 가 pet_species 를 ON DELETE RESTRICT 로 참조해서 삭제하면 실패하기 때문.
-- 따라서 기존 개구리 펫은 너구리로, 사자 펫은 호랑이로 바뀐다.
-- 식별은 appearance_key 로 한다 (name 은 이번에 바뀌는 값이 있어서).

UPDATE pet_species SET name = '너구리', appearance_key = 'raccoon' WHERE appearance_key = 'frog';
UPDATE pet_species SET name = '호랑이', appearance_key = 'tiger'   WHERE appearance_key = 'lion';
UPDATE pet_species SET name = '판다'                               WHERE appearance_key = 'panda';

UPDATE pet_species
SET tier = CASE appearance_key
    WHEN 'kitten'   THEN 'C'
    WHEN 'puppy'    THEN 'C'
    WHEN 'rabbit'   THEN 'C'
    WHEN 'turtle'   THEN 'C'
    WHEN 'deer'     THEN 'B'
    WHEN 'fox'      THEN 'B'
    WHEN 'sheep'    THEN 'B'
    WHEN 'monkey'   THEN 'B'
    WHEN 'squirrel' THEN 'B'
    WHEN 'panda'    THEN 'A'
    WHEN 'raccoon'  THEN 'A'
    WHEN 'penguin'  THEN 'A'
    WHEN 'tiger'    THEN 'A'
    WHEN 'dragon'   THEN 'S'
    WHEN 'wolf'     THEN 'S'
    WHEN 'snake'    THEN 'S'
    ELSE tier
END;
