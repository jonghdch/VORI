package com.vori.backend.furniture.dto;

import com.vori.backend.furniture.UserFurniture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 보유 가구 한 개. placed=false 면 인벤토리에 있고 분양가 보너스에도 반영되지 않는다.
 *
 * themeId 는 theme_master.id (테마 없는 가구는 null). 이름은 싣지 않는다 — 화면은 테마 현황
 * (GET /api/themes)을 이미 받고 있어 id 로 찾으면 되고, 여기서 이름까지 채우려면 배치·회수처럼
 * 자주 불리는 응답마다 테마를 다시 읽어야 한다.
 */
public record FurnitureResponse(
        Long id,
        String name,
        String category,
        String statTarget,
        BigDecimal releaseBonusPct,
        int price,
        Long themeId,
        Short positionX,
        Short positionY,
        boolean placed,
        LocalDateTime acquiredAt
) {
    public static FurnitureResponse from(UserFurniture f) {
        return new FurnitureResponse(
                f.getId(),
                f.getName(),
                f.getCategory().name(),
                f.getStatTarget().name(),
                f.getReleaseBonusPct(),
                f.getPriceGameMoney() == null ? 0 : f.getPriceGameMoney(),
                f.getThemeId(),
                f.getPositionX(),
                f.getPositionY(),
                f.isPlaced(),
                f.getAcquiredAt());
    }
}
