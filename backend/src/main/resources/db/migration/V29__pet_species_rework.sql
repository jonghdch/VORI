-- 펫 도감 개편 (16종 유지). 기존 개구리·사자 종은 참조 무결성을 유지하며 교체한다.
UPDATE pet_species SET name = '너구리', appearance_key = 'raccoon' WHERE appearance_key = 'frog';
UPDATE pet_species SET name = '호랑이', appearance_key = 'tiger' WHERE appearance_key = 'lion';
UPDATE pet_species SET name = '판다' WHERE appearance_key = 'panda';

UPDATE pet_species
SET tier = CASE appearance_key
    WHEN 'kitten' THEN 'C' WHEN 'puppy' THEN 'C' WHEN 'rabbit' THEN 'C' WHEN 'turtle' THEN 'C'
    WHEN 'deer' THEN 'B' WHEN 'fox' THEN 'B' WHEN 'sheep' THEN 'B' WHEN 'monkey' THEN 'B' WHEN 'squirrel' THEN 'B'
    WHEN 'panda' THEN 'A' WHEN 'raccoon' THEN 'A' WHEN 'penguin' THEN 'A' WHEN 'tiger' THEN 'A'
    WHEN 'dragon' THEN 'S' WHEN 'wolf' THEN 'S' WHEN 'snake' THEN 'S'
    ELSE tier
END;
