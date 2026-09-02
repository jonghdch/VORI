package com.vori.backend.furniture.dto;

import com.vori.backend.furniture.FurnitureCatalog;

import java.math.BigDecimal;

/**
 * 상점 가구 상품. releaseBonusPct 를 함께 내려 "분양가 +3%" 같은 안내를 화면에서 그릴 수 있게 한다.
 */
public record FurnitureProductResponse(
        String code,
        String name,
        String category,
        String statTarget,
        BigDecimal releaseBonusPct,
        int price
) {
    public static FurnitureProductResponse from(FurnitureCatalog c) {
        return new FurnitureProductResponse(
                c.name(),
                c.displayName(),
                c.category().name(),
                c.statTarget().name(),
                c.releaseBonusPct(),
                c.price());
    }
}
