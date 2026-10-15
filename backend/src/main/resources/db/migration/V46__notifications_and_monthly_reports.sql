-- 알림 + 월간 리포트(보이는 리포트) 정산 기록.
--
-- notifications: 헤더 종 아이콘의 알림 목록. dedupe_key 로 "같은 일로 두 번 알리지 않기" 를 보장한다
--   (예: monthly-report:2026-09, title:12, pet:5:lv15, judgment-open:2026-10-02).
-- monthly_reports: 매월 마지막 날 12시에 정산한 그 달 요약과 열람 시각. read_at 이 NULL 이면 아직 안 봄.

CREATE TABLE IF NOT EXISTS notifications (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  type        VARCHAR(30)  NOT NULL,
  title       VARCHAR(120) NOT NULL,
  body        VARCHAR(255) NULL,
  link        VARCHAR(255) NULL,
  dedupe_key  VARCHAR(100) NULL,
  created_at  DATETIME     NOT NULL,
  read_at     DATETIME     NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_notifications_user_dedupe UNIQUE (user_id, dedupe_key),
  INDEX idx_notifications_user_created (user_id, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS monthly_reports (
  id             BIGINT     NOT NULL AUTO_INCREMENT,
  user_id        BIGINT     NOT NULL,
  report_month   CHAR(7)    NOT NULL, -- "2026-09". year_month 는 MySQL 예약어라 쓰지 않는다
  expense_total  INT        NOT NULL DEFAULT 0,
  expense_count  INT        NOT NULL DEFAULT 0,
  income_total   INT        NOT NULL DEFAULT 0,
  judged_days    INT        NOT NULL DEFAULT 0,
  green_days     INT        NOT NULL DEFAULT 0,
  gray_days      INT        NOT NULL DEFAULT 0,
  red_days       INT        NOT NULL DEFAULT 0,
  generated_at   DATETIME   NOT NULL,
  read_at        DATETIME   NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_monthly_reports_user_month UNIQUE (user_id, report_month),
  CONSTRAINT fk_monthly_reports_user FOREIGN KEY (user_id) REFERENCES users(id)
);
