package com.vori.backend.pet;

/**
 * 펫 레벨 규칙 — 레벨·진화·졸업 기준이 여기 한 곳에만 있다.
 *
 * 경험치 = 4대 스탯 합(Pet.statTotal). 레벨은 경험치에서 계산하므로 따로 저장하지 않는다.
 * 레벨업에 드는 경험치는 점점 커진다: 4 + (레벨-1)/2 (내림) → 4, 4, 5, 5, 6, … 18.
 *
 *   레벨  1 ── 5 ──── 15 ──────── 30
 *        1차 │  2차  │    3차    │ 졸업
 *   누적   0   18      98          312
 *
 * 하루 판정 보상(초록 44, 노랑 24)만으로 초록 기준 일주일에 30레벨이 되도록 맞췄다.
 */
public final class PetLevel {

    public static final int MAX_LEVEL = 30;
    /** 이 레벨이 되면 2차(JUVENILE). */
    public static final int JUVENILE_LEVEL = 5;
    /** 이 레벨이 되면 3차(ADULT). */
    public static final int ADULT_LEVEL = 15;

    private static final int BASE_EXP = 4;

    private PetLevel() {}

    /** level → level+1 에 필요한 경험치. 만렙이면 0. */
    public static int expToNext(int level) {
        if (level >= MAX_LEVEL) return 0;
        return BASE_EXP + (level - 1) / 2;
    }

    /** 그 레벨이 되기 위한 최소 누적 경험치. */
    public static int minTotalFor(int level) {
        int total = 0;
        for (int lv = 1; lv < Math.min(level, MAX_LEVEL); lv++) total += expToNext(lv);
        return total;
    }

    /** 누적 경험치 → 레벨 (1 ~ MAX_LEVEL). */
    public static int levelFor(int total) {
        int level = 1;
        int need = 0;
        while (level < MAX_LEVEL) {
            need += expToNext(level);
            if (total < need) break;
            level++;
        }
        return level;
    }

    /** 레벨이 정하는 성장 단계. */
    public static PetStage stageFor(int level) {
        if (level >= ADULT_LEVEL) return PetStage.ADULT;
        if (level >= JUVENILE_LEVEL) return PetStage.JUVENILE;
        return PetStage.INFANT;
    }

    /** 단계에 들어가는 레벨. */
    public static int levelOf(PetStage stage) {
        return switch (stage) {
            case INFANT -> 1;
            case JUVENILE -> JUVENILE_LEVEL;
            case ADULT -> ADULT_LEVEL;
        };
    }
}
