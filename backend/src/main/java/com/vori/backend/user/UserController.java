package com.vori.backend.user;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.user.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 로그인한 본인 정보 조회. 인증 필요(세션).
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * GET /api/users/me — 본인 정보 + 보유 게임머니.
     *
     * principal 이 들고 있는 User 를 그대로 쓰지 않는다. 그건 로그인 시점에 세션에 담긴
     * 스냅샷이라, 알을 사거나 지출을 등록해 잔액이 바뀌어도 로그아웃 전까지 옛 값을 돌려준다.
     * 상점 잔액이 갱신되지 않는 버그가 되므로 매번 DB 에서 다시 읽는다.
     */
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getUser().getId();
        return userRepository.findById(userId)
                .map(MeResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
    }
}
