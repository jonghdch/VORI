-- 수입 종류에 '급여(SALARY)' 추가. 기존 enum 에 SALARY 끼워넣음 (OTHER 앞).
-- 대학생 외 일반 사용자(월급 수령)도 쓸 수 있게 확장.
ALTER TABLE incomes
  MODIFY COLUMN source
    ENUM('ALLOWANCE','PART_TIME','SCHOLARSHIP','SIDE_JOB','GIFT','INTEREST','SALARY','OTHER') NOT NULL;
