package com.vori.backend.savings;

/**
 * 저축 종류. DB ENUM 과 1:1 동기화.
 * - DEPOSIT: 예금·적금·CMA·비상금 통장
 * - INVEST:  주식·펀드·ETF·코인 등 투자
 */
public enum SavingType {
    DEPOSIT,
    INVEST
}
