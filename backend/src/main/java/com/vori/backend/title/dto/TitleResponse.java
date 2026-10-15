package com.vori.backend.title.dto;

import com.vori.backend.title.Title;
import com.vori.backend.title.TitleProgress;
import com.vori.backend.title.UserTitle;

import java.time.LocalDateTime;

/**
 * 업적 한 개(코드 이름은 옛 "칭호"). 획득한 것과 못 한 것을 같은 형태로 내려준다 —
 * 화면이 목록 하나만 그리면서 "다음에 딸 칭호"까지 함께 보여줄 수 있다.
 */
public record TitleResponse(
        Long id,              // 획득한 업적만 값이 있다. 장착할 때 보내는 값.
        String code,
        String name,
        String description,
        boolean acquired,
        long current,         // 현재 지표값
        long threshold,       // 목표치
        int progressPct,
        LocalDateTime acquiredAt,
        boolean hidden,       // 히든 업적인지. 못 딴 히든은 조건을 가린다(설명 "???", 현재값·목표치 0, 달성률만).
        Integer equipOrder    // 장착 순서(1~3). 장착 안 했거나 미획득이면 null
) {
    public static TitleResponse acquired(Title title, UserTitle owned,
                                         TitleProgress p) {
        return new TitleResponse(
                owned.getId(), title.getCode(), title.getName(), title.getDescription(),
                true, title.currentOf(p), title.getThreshold(), 100, owned.getAcquiredAt(),
                title.isHidden(), owned.getEquipOrder());
    }

    /** 못 딴 히든 업적의 설명 — 조건을 알려주지 않는다. */
    public static final String HIDDEN_DESCRIPTION = "???";

    public static TitleResponse locked(Title title, TitleProgress p) {
        if (title.isHidden()) {
            return new TitleResponse(null, title.getCode(), title.getName(), HIDDEN_DESCRIPTION,
                    false, 0, 0, title.progressPct(p), null, true, null);
        }
        return new TitleResponse(
                null, title.getCode(), title.getName(), title.getDescription(),
                false, title.currentOf(p), title.getThreshold(), title.progressPct(p), null,
                title.isHidden(), null);
    }
}
