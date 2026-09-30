package com.vori.backend.user;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.user.dto.MeResponse;
import com.vori.backend.user.dto.ProfileUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 본인 정보 조회. 인증 필요(세션).
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/users/me — 본인 정보 + 보유 게임머니. 값은 UserService.getMe 가 DB 에서 읽는다. */
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMe(principal.getId());
    }

    /** PUT /api/users/me — 환경설정의 프로필 수정. */
    @PutMapping
    public MeResponse updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody ProfileUpdateRequest req) {
        return MeResponse.from(userService.updateProfile(principal.getId(), req));
    }
}
