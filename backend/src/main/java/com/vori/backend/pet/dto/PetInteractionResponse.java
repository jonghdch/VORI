package com.vori.backend.pet.dto;

/**
 * 펫 상호작용(쓰다듬기·칭찬하기 등) 결과.
 * charmUp 이 true 면 이번 상호작용으로 매력이 1 올랐다 — pet 은 반영된 뒤의 상태.
 */
public record PetInteractionResponse(
        boolean charmUp,
        PetResponse pet
) {
}
