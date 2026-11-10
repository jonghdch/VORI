package com.vori.backend.auth.dto;

import com.vori.backend.user.dto.MeResponse;

/**
 * 세션 조회 응답 — 미로그인이어도 200 이다. 로그인이면 authenticated=true 와 사용자 정보, 아니면 false 와 user=null.
 * 앱 로드 때 "로그인돼 있나"를 묻는 용도라, 401 을 내면 브라우저 콘솔에 빨간 줄이 남는다.
 */
public record SessionResponse(boolean authenticated, MeResponse user) {
    public static SessionResponse of(MeResponse user) {
        return new SessionResponse(true, user);
    }

    public static SessionResponse anonymous() {
        return new SessionResponse(false, null);
    }
}
