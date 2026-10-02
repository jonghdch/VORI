package com.vori.backend.pettitle.dto;

import com.vori.backend.pettitle.PetTitleAward;

import java.time.LocalDateTime;

/**
 * 펫이 딴 칭호 1개 — 펫 응답(PetResponse)에 실려 도감 기록 카드·홈 배지에 쓰인다.
 * awardId 는 칭호를 장착할 때 보내는 값이다.
 */
public record PetTitleSummary(
        Long awardId,
        String code,
        String name,
        boolean hidden,
        LocalDateTime acquiredAt
) {
    public static PetTitleSummary of(PetTitleAward award) {
        return new PetTitleSummary(award.getId(), award.getTitle().getCode(), award.getTitle().getName(),
                award.getTitle().isHidden(), award.getAcquiredAt());
    }
}
