package com.vori.backend.theme.dto;

import com.vori.backend.theme.ThemeMaster;

import java.math.BigDecimal;

/**
 * 마이룸 테마 현황. placedCount/requiredCount 를 같이 내려 "코지 2/3 — 하나만 더!" 를
 * 화면에서 그릴 수 있게 한다.
 *
 * active 는 지금 분양가에 세트 보너스가 실제로 얹히고 있는지 — 배치된 개수가 기준을 넘겼을 때만 true.
 * 인벤토리에 쌓아둔 가구는 세지 않는다(PetService.calculateReleaseValue 와 같은 기준).
 */
public record ThemeResponse(
        Long id,
        String name,
        BigDecimal setBonusPct,
        int requiredCount,
        int placedCount,
        boolean active,
        boolean unlocked,
        String unlockTitleName
) {
    public static ThemeResponse of(ThemeMaster theme, int placedCount, boolean unlocked) {
        int required = theme.getRequiredCount() == null ? 0 : theme.getRequiredCount();
        return new ThemeResponse(
                theme.getId(),
                theme.getName(),
                theme.getSetBonusPct(),
                required,
                placedCount,
                required > 0 && placedCount >= required,
                unlocked,
                theme.getUnlockTitleName());
    }

    /** 달성 근접도(%) — 정렬용. 발동 후에도 100 을 넘기지 않는다. */
    public int progressPct() {
        if (requiredCount <= 0) return 0;
        return Math.min(100, placedCount * 100 / requiredCount);
    }
}
