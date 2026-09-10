package com.vori.backend.title;

import java.util.function.ToLongFunction;

/**
 * 칭호 조건 계산 타입.
 * 칭호 이름·설명·목표값은 DB titles 에 두고, 지표 계산 로직만 코드가 책임진다.
 */
public enum TitleMetricType {
    TOTAL_SAVED(TitleProgress::totalSaved),
    EXPENSE_COUNT(TitleProgress::expenseCount),
    GOALS_ACHIEVED(TitleProgress::goalsAchieved),
    PETS_RELEASED(TitleProgress::petsReleased),
    S_TIER_PETS(TitleProgress::sTierPets),
    AI_ANSWERS(TitleProgress::aiAnswers),
    RECEIPT_SCANS(TitleProgress::receiptScans);

    private final ToLongFunction<TitleProgress> current;

    TitleMetricType(ToLongFunction<TitleProgress> current) {
        this.current = current;
    }

    public long currentOf(TitleProgress progress) {
        return current.applyAsLong(progress);
    }
}
