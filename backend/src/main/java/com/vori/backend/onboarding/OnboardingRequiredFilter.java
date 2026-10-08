package com.vori.backend.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.common.ErrorResponse;
import com.vori.backend.user.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 소비 프로필 설문을 마치지 않은 계정은 설문과 로그인 관련 API 만 쓸 수 있다.
 *
 * 화면(App.js)만 막으면 API 를 직접 불러 설문 없이 지출 등록·판정을 할 수 있었다. 설문이 판정 기준선
 * (BaselineSeeder)을 만들기 때문에 서버에서도 강제한다. 관리자는 설문 대상이 아니다.
 *
 * AccountStatusFilter 뒤에 둔다 — 역할이 DB 기준으로 갱신된 다음에 관리자 여부를 본다.
 * @Component 로 두지 않는다(서블릿 필터로 한 번 더 등록돼 두 번 돈다). SecurityConfig 가 생성한다.
 */
public class OnboardingRequiredFilter extends OncePerRequestFilter {

    /** 설문 전에도 열어 두는 경로. 로그인 상태 확인·로그아웃과 설문 저장·조회·완료. */
    private static final List<String> ALLOWED_PREFIXES = List.of("/api/auth/", "/api/onboarding/");
    /** 설문 전에도 여는 단일 경로. 약관대로 가입 직후 설문을 그만둔 계정도 언제든 탈퇴할 수 있어야 한다. */
    private static final Set<String> ALLOWED_PATHS = Set.of("/api/users/me/withdrawal");

    private final UserSpendingProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    public OnboardingRequiredFilter(UserSpendingProfileRepository profileRepository, ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/") || ALLOWED_PATHS.contains(path)
                || ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)
                || principal.getRole() == Role.ADMIN
                || profileRepository.existsById(principal.getId())) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(HttpStatus.FORBIDDEN,
                "소비 프로필 설문을 먼저 완료해 주세요", request.getRequestURI()));
    }
}
