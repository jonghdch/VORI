package com.vori.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vori.backend.sanction.SanctionPolicy;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 세션은 로그인 시점의 판단을 들고 있으므로, 제재·역할은 요청마다 DB 로 다시 확인해야 한다.
 * 재현: 로그인한 사용자를 BAN 해도 그 세션으로 /api/users/me·프로필 수정이 200 이었다.
 */
class AccountStatusFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SanctionPolicy sanctionPolicy = mock(SanctionPolicy.class);
    private final AccountStatusFilter filter =
            new AccountStatusFilter(userRepository, sanctionPolicy, new ObjectMapper().findAndRegisterModules());
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private User user(Role role) {
        return User.builder().id(1L).email("a@vori.com").nickname("닉").passwordHash("h").role(role).build();
    }

    private void loginAs(Role role) {
        UserPrincipal p = new UserPrincipal(user(role), false);
        p.eraseCredentials();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        req.setSession(new MockHttpSession());
        return req;
    }

    @Test
    void sanctionedSessionIsCutOff() throws Exception {
        loginAs(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.USER)));
        when(sanctionPolicy.isBlocked(1L)).thenReturn(true);
        MockHttpServletRequest req = request();
        MockHttpSession session = (MockHttpSession) req.getSession();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("제재 중인 계정입니다"));
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }

    @Test
    void deletedUserSessionIsCutOff() throws Exception {
        loginAs(Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(request(), res, chain);

        assertEquals(401, res.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void changedRoleIsReflectedWithoutRelogin() throws Exception {
        loginAs(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.USER)));
        MockHttpServletRequest req = request();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(Role.USER, ((UserPrincipal) auth.getPrincipal()).getRole());
        assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
        assertNull(((UserPrincipal) auth.getPrincipal()).getPassword());
        verify(chain).doFilter(eq(req), any());
    }

    @Test
    void normalSessionPassesUnchanged() throws Exception {
        loginAs(Role.USER);
        Authentication before = SecurityContextHolder.getContext().getAuthentication();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.USER)));

        filter.doFilter(request(), new MockHttpServletResponse(), chain);

        assertSame(before, SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void sanctionedUserCanStillLogOut() throws Exception {
        loginAs(Role.USER);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/logout");
        req.setServletPath("/api/auth/logout");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(userRepository, sanctionPolicy);
        verify(chain).doFilter(eq(req), eq(res));
    }

    @Test
    void anonymousRequestSkipsDbLookup() throws Exception {
        filter.doFilter(request(), new MockHttpServletResponse(), chain);

        verifyNoInteractions(userRepository, sanctionPolicy);
        verify(chain).doFilter(any(), any());
    }
}
