-- 에픽(A) 등급의 호랑이를 사자로 되돌린다 (V16 에서 사자 → 호랑이로 바꿨던 행).
-- 행을 그대로 두고 이름·외형 키만 바꾸므로 기존 호랑이 펫은 사자가 된다. 등급(A)은 그대로.
-- 새 DB 에서는 0행이고 PetSpeciesSeeder 가 사자로 넣는다.
UPDATE pet_species SET name = '사자', appearance_key = 'lion' WHERE appearance_key = 'tiger';
