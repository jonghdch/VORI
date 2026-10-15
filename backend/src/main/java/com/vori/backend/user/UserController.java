package com.vori.backend.user;

import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.user.dto.MeResponse;
import com.vori.backend.user.dto.ProfileUpdateRequest;
import com.vori.backend.user.dto.WithdrawalRequest;
import com.vori.backend.user.dto.WithdrawalResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 로그인한 본인 정보 조회·수정·탈퇴. 인증 필요(세션).
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

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

    /**
     * POST /api/users/me/withdrawal — 회원 탈퇴 신청. 유예 기간 뒤 영구 삭제되고, 그 전에 로그인하면 복구된다.
     * 접수되면 이 세션을 바로 끊는다(다른 기기의 세션은 AccountStatusFilter 가 끊는다).
     * 400 = 본인 확인 실패, 403 = 관리자 계정, 409 = 이미 신청함.
     */
    @PostMapping("/withdrawal")
    public WithdrawalResponse withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody WithdrawalRequest req,
                                       HttpServletRequest request, HttpServletResponse response) {
        WithdrawalResponse result = accountDeletionService.request(principal.getId(), req, LocalDateTime.now());
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return result;
    }
}
