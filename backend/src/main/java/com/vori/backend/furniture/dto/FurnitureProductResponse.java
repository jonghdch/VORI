package com.vori.backend.furniture.dto;

import com.vori.backend.furniture.FurnitureCatalog;
import com.vori.backend.theme.ThemeMaster;

import java.math.BigDecimal;

/**
 * 상점 가구 상품. releaseBonusPct 를 함께 내려 "분양가 +3%" 같은 안내를 화면에서 그릴 수 있게 한다.
 *
 * 잠긴 가구도 숨기지 않고 locked=true 로 내려준다 — 해금 조건이 보여야 목표가 된다
 * (미획득 칭호를 진행률과 함께 내려주는 것과 같은 기조). 구매 시도는 서버가 403 으로 막는다.
 */
public record FurnitureProductResponse(
        String code,
        String name,
        String category,
        String statTarget,
        BigDecimal releaseBonusPct,
        int price,
        String themeName,
        BigDecimal themeSetBonusPct,
        boolean locked,
        String unlockTitleName
) {
    /** theme 는 카탈로그의 themeName 으로 찾은 테마(없으면 null), locked 는 해금 판정 결과. */
    public static FurnitureProductResponse from(FurnitureCatalog c, ThemeMaster theme, boolean locked) {
        return new FurnitureProductResponse(
                c.name(),
                c.displayName(),
                c.category().name(),
                c.statTarget().name(),
                c.releaseBonusPct(),
                c.price(),
                theme == null ? null : theme.getName(),
                theme == null ? null : theme.getSetBonusPct(),
                locked,
                theme == null ? null : theme.getUnlockTitleName());
    }
}
