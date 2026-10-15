-- 수입을 어느 계좌·현금으로 받았는지. 내 카드·계좌(payment_instruments) 중 입출금·예적금·현금만 고를 수 있다.
-- 고르지 않았거나 V54 이전 수입은 NULL. 카드·계좌를 지우면 연결만 비운다.
ALTER TABLE incomes
    ADD COLUMN payment_instrument_id BIGINT NULL AFTER payment_method,
    ADD CONSTRAINT fk_incomes_payment_instrument FOREIGN KEY (payment_instrument_id)
        REFERENCES payment_instruments (id) ON DELETE SET NULL;
