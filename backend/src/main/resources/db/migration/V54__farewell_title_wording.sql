SET NAMES utf8mb4;

-- 펫을 30레벨까지 키워 내보내는 일을 「분양」·「졸업」 대신 「배웅」으로 부른다
-- (스토리 — 루미나의 친구를 달나라로 돌려보낸다). V11·V41 시드의 칭호 문구를 같은 말로 맞춘다.
-- 관리자가 칭호 관리 화면에서 이미 고친 문구는 건드리지 않도록 시드 원문과 같을 때만 바꾼다.
UPDATE titles SET name = '첫 배웅'
  WHERE code = 'PET_FIRST_RELEASE' AND name = '첫 분양';
UPDATE titles SET description = '펫 1마리 배웅'
  WHERE code = 'PET_FIRST_RELEASE' AND description = '펫 1마리 분양';
UPDATE titles SET description = '펫 5마리 배웅'
  WHERE code = 'PET_COLLECTOR' AND description = '펫 5마리 분양';
UPDATE titles SET description = '펫 10마리 배웅'
  WHERE code = 'PET_VETERAN' AND description = '펫 10마리 분양';
UPDATE titles SET description = '서로 다른 종 5종 배웅'
  WHERE code = 'DEX_COLLECTOR' AND description = '서로 다른 종 5종 졸업';
UPDATE titles SET description = '서로 다른 종 16종 모두 배웅'
  WHERE code = 'DEX_COMPLETE' AND description = '서로 다른 종 16종 모두 졸업';
