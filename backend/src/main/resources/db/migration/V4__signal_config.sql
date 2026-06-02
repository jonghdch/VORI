-- 합리성 신호 판정 임계값 설정 (관리자 조정 가능). 단일 행(id=1).
-- ExpenseService 가 z-score → signal(RED/GRAY/GREEN) 판정 시 이 값을 읽는다.
-- 기존 코드 상수(Z_RED=2.0, Z_GREEN=1.0) 를 DB 로 옮긴 것.
CREATE TABLE signal_config (
  id         INT          NOT NULL PRIMARY KEY,
  z_red      DECIMAL(4,2) NOT NULL,
  z_green    DECIMAL(4,2) NOT NULL,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO signal_config (id, z_red, z_green) VALUES (1, 2.00, 1.00);
