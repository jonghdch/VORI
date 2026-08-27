package com.vori.backend.pet.dto;

import com.vori.backend.pet.EggGrade;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 상점 알 상품. 프론트 ShopPage 의 하드코딩된 SHOP_ITEMS 를 대체한다.
 * probabilities 를 함께 내려 "이 알은 S 가 20%" 같은 안내를 화면에서 그릴 수 있게 한다.
 */
public record EggProductResponse(
        String grade,
        String name,
        int price,
        Map<String, Integer> probabilities
) {
    public static EggProductResponse from(EggGrade g) {
        Map<String, Integer> probs = new LinkedHashMap<>();
        g.distribution().forEach((tier, pct) -> probs.put(tier.name(), pct));
        return new EggProductResponse(g.name(), g.displayName(), g.price(), probs);
    }
}
