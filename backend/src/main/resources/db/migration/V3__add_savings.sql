-- ============================================================
-- V3 — savings 테이블 신규
-- ============================================================
-- 저축 = 예금/투자 기록. 단순 단방향 기록 (수익률·시세 추적 X).
-- 수입·지출과 별개 흐름. AI 분석 없음.
-- ============================================================

CREATE TABLE savings (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    saved_at    DATE         NOT NULL,
    amount      INT UNSIGNED NOT NULL,
    item        VARCHAR(100) NOT NULL,
    saving_type ENUM('DEPOSIT','INVEST') NOT NULL,
    note        VARCHAR(200) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_savings_user_date (user_id, saved_at),
    CONSTRAINT fk_savings_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
