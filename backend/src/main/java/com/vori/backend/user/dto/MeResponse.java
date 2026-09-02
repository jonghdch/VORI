package com.vori.backend.user.dto;

import com.vori.backend.user.Role;
import com.vori.backend.user.User;

/**
 * 로그인한 본인 정보. 상점의 보유 코인 배지, 헤더 닉네임, 튜토리얼 분기에 쓴다.
 *
 * gameMoney·totalSaved 는 알 구매·지출 등록으로 계속 바뀌므로 세션에 캐시된 값이 아니라
 * 매번 DB 에서 읽은 값이어야 한다 (UserController 참고).
 */
public record MeResponse(
        Long id,
        String email,
        String nickname,
        String name,
        Role role,
        int gameMoney,
        int totalSaved,
        boolean tutorialDone
) {
    public static MeResponse from(User u) {
        return new MeResponse(
                u.getId(),
                u.getEmail(),
                u.getNickname(),
                u.getName(),
                u.getRole(),
                nz(u.getGameMoney()),
                nz(u.getTotalSaved()),
                Boolean.TRUE.equals(u.getTutorialDone()));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
