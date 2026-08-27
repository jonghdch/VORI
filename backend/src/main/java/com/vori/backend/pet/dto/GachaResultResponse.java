package com.vori.backend.pet.dto;

/** 알 개봉 결과. 어떤 알에서 어떤 펫이 나왔는지 + 차감 후 잔액. */
public record GachaResultResponse(
        Long eggId,
        PetResponse pet,
        int remainGameMoney
) {}
