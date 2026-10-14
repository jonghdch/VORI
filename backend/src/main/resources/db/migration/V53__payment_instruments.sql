-- 내 카드·계좌(결제수단) — 사용자가 직접 등록한다. 자동 연동이 아니라 "이런 카드를 쓴다"는 꼬리표.
-- 카드 번호·유효기간 같은 민감 정보는 받지 않는다. 삭제는 archived_at 만 채워 과거 지출의 연결을 지킨다.
CREATE TABLE payment_instruments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    kind            ENUM('DEBIT','CREDIT','CHECKING','SAVINGS','CASH') NOT NULL,
    issuer          VARCHAR(20)  NOT NULL,
    name            VARCHAR(40)  NOT NULL,
    opening_balance INT UNSIGNED NOT NULL DEFAULT 0,
    archived_at     DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_payment_instruments_user (user_id, archived_at),
    CONSTRAINT fk_payment_instruments_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 지출이 어느 카드·계좌로 나갔는지. 고르지 않았으면 NULL(결제수단 종류만 payment_method 에 남는다).
ALTER TABLE expenses
    ADD COLUMN payment_instrument_id BIGINT NULL AFTER payment_method,
    ADD CONSTRAINT fk_expenses_payment_instrument FOREIGN KEY (payment_instrument_id)
        REFERENCES payment_instruments (id) ON DELETE SET NULL;
