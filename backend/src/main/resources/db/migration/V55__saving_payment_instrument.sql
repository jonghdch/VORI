-- 저축을 어느 저축 통장에 모았는지. 내 카드·계좌(payment_instruments) 중 저축 통장(SAVINGS)만 고를 수 있다.
-- 투자 유형이거나 고르지 않았거나 V55 이전 저축은 NULL. 카드·계좌를 지우면 연결만 비운다.
ALTER TABLE savings
    ADD COLUMN payment_instrument_id BIGINT NULL AFTER saving_type,
    ADD CONSTRAINT fk_savings_payment_instrument FOREIGN KEY (payment_instrument_id)
        REFERENCES payment_instruments (id) ON DELETE SET NULL;
