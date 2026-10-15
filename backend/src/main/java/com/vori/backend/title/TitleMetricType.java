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
    RECEIPT_SCANS(TitleProgress::receiptScans),
    LOGIN_COUNT(TitleProgress::loginCount),
    PET_INTERACTIONS(TitleProgress::petInteractions),
    PETS_HATCHED(TitleProgress::petsHatched),
    SPECIES_GRADUATED(TitleProgress::speciesGraduated),
    PET_TITLES_TOTAL(TitleProgress::petTitlesTotal),
    PET_TITLE_KINDS(TitleProgress::petTitleKinds),
    PET_TITLES_ON_ONE_PET(TitleProgress::petTitlesOnOnePet),
    HIDDEN_PET_TITLES(TitleProgress::hiddenPetTitles);

    private final ToLongFunction<TitleProgress> current;

    TitleMetricType(ToLongFunction<TitleProgress> current) {
        this.current = current;
    }

    public long currentOf(TitleProgress progress) {
        return current.applyAsLong(progress);
    }
}
