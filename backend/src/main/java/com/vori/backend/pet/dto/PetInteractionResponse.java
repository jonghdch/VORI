package com.vori.backend.pet.dto;

import com.vori.backend.title.dto.GrantedTitle;

import java.util.List;

/**
 * 펫 상호작용(쓰다듬기·칭찬하기 등) 결과.
 * charmUp 이 true 면 이번 상호작용으로 매력이 1 올랐다 — pet 은 반영된 뒤의 상태.
 * newTitles 는 이번 상호작용으로 펫이 새로 받은 칭호(대개 비어 있다).
 */
public record PetInteractionResponse(
        boolean charmUp,
        PetResponse pet,
        List<GrantedTitle> newTitles
) {}
