package com.vori.backend.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.auth.UserPrincipal;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 소비 프로필 설문을 마치지 않은 계정은 설문·로그인 관련 API 외에는 쓸 수 없다.
 * 화면만 막으면 API 를 직접 불러 우회할 수 있었다(설문 없이 지출 등록·판정 가능).
 */
class OnboardingRequiredFilterTest {

    private final UserSpendingProfileRepository profiles = mock(UserSpendingProfileRepository.class);
    private final OnboardingRequiredFilter filter =
            new OnboardingRequiredFilter(profiles, new ObjectMapper().findAndRegisterModules());
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Role role) {
        UserPrincipal p = new UserPrincipal(User.builder().id(1L).email("a@vori.com").passwordHash("h").role(role).build(), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        return req;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/expenses", "/api/users/me", "/api/daily-judgments/today", "/api/attendance", "/api/eggs/buy"})
    void 설문_전에는_다른_API_를_막는다(String path) throws Exception {
        loginAs(Role.USER);
        when(profiles.existsById(1L)).thenReturn(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(request("POST", path), res, chain);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("설문"));
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/onboarding/status", "/api/onboarding/spending-profile", "/api/auth/me", "/api/auth/logout"})
    void 설문_전에도_설문과_로그인_API_는_연다(String path) throws Exception {
        loginAs(Role.USER);
        when(profiles.existsById(1L)).thenReturn(false);

        filter.doFilter(request("GET", path), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void 설문_전에도_회원_탈퇴는_열어_둔다() throws Exception {
        // 약관대로 가입 직후 설문을 그만둔 계정도 언제든 탈퇴할 수 있어야 한다
        loginAs(Role.USER);
        when(profiles.existsById(1L)).thenReturn(false);

        filter.doFilter(request("POST", "/api/users/me/withdrawal"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void 설문을_마쳤으면_통과() throws Exception {
        loginAs(Role.USER);
        when(profiles.existsById(1L)).thenReturn(true);

        filter.doFilter(request("POST", "/api/expenses"), new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    void 관리자와_비로그인_요청은_확인하지_않는다() throws Exception {
        loginAs(Role.ADMIN);
        filter.doFilter(request("GET", "/api/admin/users"), new MockHttpServletResponse(), chain);
        SecurityContextHolder.clearContext();
        // 허용 목록이 아닌 경로로 — 비로그인 분기 자체를 확인한다(인증은 뒤의 AuthorizationFilter 몫)
        filter.doFilter(request("POST", "/api/expenses"), new MockHttpServletResponse(), chain);

        verifyNoInteractions(profiles);
        verify(chain, times(2)).doFilter(any(), any());
    }
}
