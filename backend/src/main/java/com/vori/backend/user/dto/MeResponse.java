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
        Integer age,
        String job,
        Integer monthlyIncome,
        Role role,
        int gameMoney,
        int totalSaved,
        boolean tutorialDone,
        // 비밀번호로 로그인하는 계정인가 — 탈퇴 본인 확인을 비밀번호로 받을지(아니면 구글 가입이라 확인 문구) 화면이 정한다
        boolean hasPassword,
        // 이번 로그인으로 탈퇴가 취소됐는가. 로그인 응답에서만 true 가 될 수 있다 (AuthController)
        boolean accountRestored
) {
    public static MeResponse from(User u) {
        return new MeResponse(
                u.getId(),
                u.getEmail(),
                u.getNickname(),
                u.getName(),
                u.getAge(),
                u.getJob(),
                u.getMonthlyIncome(),
                u.getRole(),
                nz(u.getGameMoney()),
                nz(u.getTotalSaved()),
                Boolean.TRUE.equals(u.getTutorialDone()),
                u.getPasswordHash() != null,
                false);
    }

    /** 로그인으로 탈퇴가 취소됐다는 표시를 붙인다. */
    public MeResponse withAccountRestored() {
        return new MeResponse(id, email, nickname, name, age, job, monthlyIncome, role,
                gameMoney, totalSaved, tutorialDone, hasPassword, true);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
