package com.vori.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.common.ErrorResponse;
import com.vori.backend.sanction.SanctionPolicy;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * 로그인한 요청마다 계정 상태를 DB 로 다시 확인한다.
 *
 * 세션에는 로그인 시점의 판단(제재 여부·역할)이 남아 있어, 로그인한 채로 BAN 당한 사용자가
 * 로그아웃 전까지 앱을 계속 쓸 수 있었다. 여기서 매 요청 확인한다.
 * - 제재 중: 세션을 끊고 403 (로그인 거절과 같은 문구)
 * - 계정이 사라짐: 세션을 끊고 401
 * - 역할이 바뀜: 권한을 새로 만들어 세션에 저장 (다시 로그인하지 않아도 반영)
 *
 * @Component 로 두지 않는다. 두면 Spring Boot 가 서블릿 필터로도 등록해 두 번 돈다.
 * SecurityConfig 가 생성해 Security 필터 체인에만 넣는다.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SanctionPolicy sanctionPolicy;
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository securityContextRepository;

    public AccountStatusFilter(UserRepository userRepository, SanctionPolicy sanctionPolicy,
                               ObjectMapper objectMapper, SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.sanctionPolicy = sanctionPolicy;
        this.objectMapper = objectMapper;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * 세션을 끝내거나 새로 만드는 요청은 옛 세션 상태로 막지 않는다.
     * - 로그아웃: 막으면 제재된 사용자의 로그아웃이 403 에러가 된다
     * - 로그인·가입: 막으면 제재된 옛 세션이 남은 브라우저에서 다른 계정 로그인이 첫 시도에 막힌다.
     *   로그인 자체의 제재 확인은 CustomUserDetailsService 가 한다
     */
    private static final Set<String> SESSION_ENDPOINTS =
        Set.of("/api/auth/logout", "/api/auth/login", "/api/auth/signup");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SESSION_ENDPOINTS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<User> current = userRepository.findById(principal.getId());
        if (current.isEmpty()) {
            reject(request, response, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
            return;
        }
        if (sanctionPolicy.isBlocked(principal.getId())) {
            reject(request, response, HttpStatus.FORBIDDEN, "제재 중인 계정입니다");
            return;
        }

        User user = current.get();
        if (user.getRole() != principal.getRole()) {
            UserPrincipal refreshed = new UserPrincipal(user, false);
            refreshed.eraseCredentials();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                refreshed, null, refreshed.getAuthorities()));
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        HttpStatus status, String message) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
            ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
