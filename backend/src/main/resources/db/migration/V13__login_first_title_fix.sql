SET NAMES utf8mb4;

-- V12 에서 업적명("절약의 첫걸음")과 조건 문구를 한 필드에 합쳐 넣었던 걸 바로잡는다.
-- 업적 카드에는 조건만 간결하게 보이도록 description 을 조건 문구만 남긴다.
-- (V12 가 이미 적용됐을 수 있어 그 파일을 고치는 대신 새 마이그레이션으로 정정한다.)
UPDATE titles
SET description = '최초 1회 로그인'
WHERE code = 'LOGIN_FIRST';
