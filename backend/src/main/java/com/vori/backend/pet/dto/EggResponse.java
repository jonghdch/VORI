package com.vori.backend.pet.dto;

import com.vori.backend.pet.Egg;

import java.time.LocalDateTime;

/** 보유 알 1개. opened=false 인 것만 개봉할 수 있다. */
public record EggResponse(
        Long id,
        String gradeName,
        int price,
        LocalDateTime purchasedAt,
        LocalDateTime openedAt,
        boolean opened
) {
    public static EggResponse from(Egg e) {
        return new EggResponse(
                e.getId(),
                e.getGradeName(),
                e.getPriceGameMoney(),
                e.getPurchasedAt(),
                e.getOpenedAt(),
                e.isOpened());
    }
}
