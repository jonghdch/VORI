package com.vori.backend.paymentinstrument;

import com.vori.backend.common.PaymentMethod;

/**
 * 등록한 결제수단의 종류. DB ENUM 과 1:1 동기화.
 * paymentMethod = 이 카드·계좌로 낸 지출의 expenses.payment_method 값. 예적금은 결제에 쓰지 않아 null.
 */
public enum InstrumentKind {
    DEBIT(PaymentMethod.DEBIT),
    CREDIT(PaymentMethod.CREDIT),
    CHECKING(PaymentMethod.TRANSFER),
    SAVINGS(null),
    CASH(PaymentMethod.CASH);

    private final PaymentMethod paymentMethod;

    InstrumentKind(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public PaymentMethod paymentMethod() {
        return paymentMethod;
    }
}
