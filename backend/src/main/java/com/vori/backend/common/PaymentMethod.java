package com.vori.backend.common;

/**
 * 결제·수금 수단. expenses·incomes 공용.
 * 미입력은 NULL 로 처리 (OTHER 없음).
 */
public enum PaymentMethod {
    CASH,        // 현금
    DEBIT,       // 체크카드
    CREDIT,      // 신용카드
    TRANSFER,    // 계좌이체
    MOBILE_PAY   // 모바일페이 (삼성페이·토스페이·카카오페이 등)
}
