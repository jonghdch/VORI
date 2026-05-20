-- expenses / incomes 에 일반 가계부 표준 필드 보강
-- 결정 사유는 docs/db-spec.md 변경 이력 #12 참조

SET NAMES utf8mb4;

ALTER TABLE expenses
  ADD COLUMN payment_method ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY') NULL AFTER amount,
  ADD COLUMN memo           VARCHAR(200) NULL                                      AFTER item,
  ADD COLUMN is_recurring   BOOLEAN NOT NULL DEFAULT FALSE                         AFTER memo,
  ADD COLUMN updated_at     DATETIME NULL ON UPDATE CURRENT_TIMESTAMP              AFTER created_at;

ALTER TABLE incomes
  ADD COLUMN payment_method ENUM('CASH','DEBIT','CREDIT','TRANSFER','MOBILE_PAY') NULL AFTER amount,
  ADD COLUMN is_recurring   BOOLEAN NOT NULL DEFAULT FALSE                         AFTER source,
  ADD COLUMN updated_at     DATETIME NULL ON UPDATE CURRENT_TIMESTAMP              AFTER created_at;
