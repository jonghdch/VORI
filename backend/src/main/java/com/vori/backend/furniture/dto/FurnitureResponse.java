package com.vori.backend.furniture.dto;

import com.vori.backend.furniture.UserFurniture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 보유 가구 한 개. placed=false 면 인벤토리에 있고 분양가 보너스에도 반영되지 않는다.
 */
public record FurnitureResponse(
        Long id,
        String name,
        String category,
        String statTarget,
        BigDecimal releaseBonusPct,
        int price,
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
                f.getPositionX(),
                f.getPositionY(),
                f.isPlaced(),
                f.getAcquiredAt());
    }
}
