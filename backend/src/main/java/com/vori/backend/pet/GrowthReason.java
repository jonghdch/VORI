package com.vori.backend.pet;

public enum GrowthReason {
    EXPENSE_SAVING,
    GOAL_ACHIEVED,
    BONUS,
    DAILY_JUDGMENT,
    ATTENDANCE_ITEM,
    FURNITURE_BONUS,
    /** 펫 상호작용(우클릭) 매력 보너스. 하루 상한을 세려고 다른 BONUS 와 나눈다. */
    PET_INTERACTION
}
