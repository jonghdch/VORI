package com.vori.backend.pettitle.dto;

import java.util.List;

/**
 * 칭호 탭 — 지금 키우는 펫의 칭호 과제와 진행도.
 * 키우는 펫이 없으면 petId 가 null 이고, 과제는 0% 로 미리 보여 준다. 못 딴 히든은 설명이 "???" 이다.
 */
public record PetTitleBoardResponse(
        Long petId,
        String petName,
        String speciesName,
        List<PetTitleItem> titles
) {}
