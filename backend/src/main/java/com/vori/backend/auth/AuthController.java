package com.vori.backend.auth;

import com.vori.backend.auth.dto.AuthResponse;
import com.vori.backend.auth.dto.GoogleLoginRequest;
import com.vori.backend.auth.dto.LoginRequest;
import com.vori.backend.auth.dto.SignupRequest;
import com.vori.backend.user.User;
import com.vori.backend.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final CustomUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository =
        new HttpSessionSecurityContextRepository();

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        User user = userService.signup(req);
        return AuthResponse.from(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req,
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
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest req,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(req.credential());
        User user = userService.findOrCreateGoogleUser(identity.sub(), identity.email(), identity.name());

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(user.getEmail());
        if (!principal.isAccountNonLocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "제재 중인 계정입니다");
        }
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        return establishSession(auth, request, response);
    }

    /** 인증 성공 후 세션 수립 — 이메일 로그인·구글 로그인이 공유한다. */
    private AuthResponse establishSession(Authentication auth,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        // 세션 고정(session fixation) 방어 — 로그인 성공 시 세션 ID 회전.
        // formLogin 을 꺼서 필터의 changeSessionId 전략을 안 타므로 직접 수행.
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        userService.recordLogin(principal.getUser().getId());
        return AuthResponse.from(principal.getUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        }
        return AuthResponse.from(principal.getUser());
    }
}
