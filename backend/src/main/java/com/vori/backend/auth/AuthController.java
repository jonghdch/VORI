package com.vori.backend.auth;

import com.vori.backend.auth.dto.AuthResponse;
import com.vori.backend.auth.dto.GoogleLoginRequest;
import com.vori.backend.auth.dto.LoginRequest;
import com.vori.backend.auth.dto.SignupRequest;
import com.vori.backend.user.AccountDeletionService;
import com.vori.backend.user.User;
import com.vori.backend.user.UserService;
import com.vori.backend.user.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final CustomUserDetailsService userDetailsService;
    private final AccountDeletionService accountDeletionService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        User user = userService.signup(req);
        return AuthResponse.from(user);
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest req,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
            );

            return establishSession(auth, request, response);
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");
        } catch (LockedException e) {
            // 제재(정지·영구정지) 계정 — CustomUserDetailsService 가 잠금 처리한 경우
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "제재 중인 계정입니다");
        }
    }

    /**
     * 구글 로그인. 프론트의 Google Identity Services 버튼이 준 ID 토큰을 검증한 뒤
     * 계정을 찾거나 만들고, 이메일 로그인과 똑같은 세션을 만든다.
     * 비밀번호를 거치지 않으므로 AuthenticationManager 대신 검증된 principal 로 직접 인증 객체를 만든다.
     * 제재(정지·영구정지) 판정은 CustomUserDetailsService 가 같은 규칙으로 한다.
     */
    @PostMapping("/google")
    public MeResponse google(@Valid @RequestBody GoogleLoginRequest req,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(req.credential());
        User user;
        try {
            user = userService.findOrCreateGoogleUser(identity.sub(), identity.email(), identity.name());
        } catch (DataIntegrityViolationException e) {
            // 첫 로그인이 동시에 두 번 와 한쪽이 먼저 만들었다 — 새 트랜잭션으로 다시 찾는다
            user = userService.findOrCreateGoogleUser(identity.sub(), identity.email(), identity.name());
        }
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(user.getEmail());
        if (!principal.isAccountNonLocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "제재 중인 계정입니다");
        }
        // ProviderManager 를 거치지 않으므로 비밀번호 해시를 직접 지운다 — 세션에 남기지 않는다.
        principal.eraseCredentials();
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        return establishSession(auth, request, response);
    }

    /** 인증 성공 후 세션 수립 — 이메일 로그인·구글 로그인이 공유한다. */
    private MeResponse establishSession(Authentication auth,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        // 탈퇴 대기 계정이면 복구한다. 유예 기간이 지났으면 여기서 401 — 세션을 만들기 전에 판정한다
        boolean restored = accountDeletionService.restoreOnLogin(principal.getId(), LocalDateTime.now());

        // 세션 고정(session fixation) 방어 — 로그인 성공 시 세션 ID 회전.
        // formLogin 을 꺼서 필터의 changeSessionId 전략을 안 타므로 직접 수행.
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        userService.recordLogin(principal.getId());
        MeResponse me = userService.getMe(principal.getId());
        // 탈퇴 대기 계정이 로그인해 복구됐으면 화면이 안내할 수 있게 표시한다
        return restored ? me.withAccountRestored() : me;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return ResponseEntity.noContent().build();
    }

    /** 세션 확인 + 본인 정보. 세션에는 신원만 있으므로 값은 DB 에서 읽는다(프로필 수정이 바로 반영). */
    @GetMapping("/me")
    public MeResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        }
        return userService.getMe(principal.getId());
    }
}
