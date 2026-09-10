package com.vori.backend.title.dto;

import com.vori.backend.title.Title;
import com.vori.backend.title.TitleProgress;
import com.vori.backend.title.UserTitle;

import java.time.LocalDateTime;

/**
 * 칭호 한 개. 획득한 것과 못 한 것을 같은 형태로 내려준다 —
 * 화면이 목록 하나만 그리면서 "다음에 딸 칭호"까지 함께 보여줄 수 있다.
 */
public record TitleResponse(
        Long id,              // 획득한 칭호만 값이 있다. 장착 요청에 쓰는 값.
        String code,
        String name,
        String description,
        boolean acquired,
        boolean active,       // 현재 장착 중인지
        long current,         // 현재 지표값
        long threshold,       // 목표치
        int progressPct,
        LocalDateTime acquiredAt
) {
    public static TitleResponse acquired(Title title, UserTitle owned,
                                         TitleProgress p, boolean active) {
        return new TitleResponse(
                owned.getId(), title.getCode(), title.getName(), title.getDescription(),
                true, active, title.currentOf(p), title.getThreshold(), 100, owned.getAcquiredAt());
    }

    public static TitleResponse locked(Title title, TitleProgress p) {
        return new TitleResponse(
                null, title.getCode(), title.getName(), title.getDescription(),
                false, false, title.currentOf(p), title.getThreshold(), title.progressPct(p), null);
    }
}
