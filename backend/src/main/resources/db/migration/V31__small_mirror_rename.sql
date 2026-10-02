-- 구매 당시 이름을 저장한 기존 거울도 카탈로그 표기와 맞춘다.
UPDATE user_furniture SET name = '거울' WHERE category = 'MIRROR' AND name = '작은 거울';
