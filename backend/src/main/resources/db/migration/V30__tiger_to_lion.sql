-- V29에서 호랑이로 바꾼 에픽 종을 최종 사자 표기로 맞춘다.
UPDATE pet_species SET name = '사자', appearance_key = 'lion' WHERE appearance_key = 'tiger';
