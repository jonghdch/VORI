package com.vori.backend.auth;

import com.vori.backend.auth.dto.GoogleLoginRequest;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 구글 로그인은 ProviderManager 를 거치지 않아 비밀번호 해시가 자동으로 지워지지 않는다.
 * 구글을 연결한 이메일 계정은 해시가 있으므로, 직접 지우지 않으면 세션에 남는다.
 */
class GoogleLoginSessionTest {

    private final UserService userService = mock(UserService.class);
    private final GoogleTokenVerifier verifier = mock(GoogleTokenVerifier.class);
    private final CustomUserDetailsService userDetails = mock(CustomUserDetailsService.class);
    private final AuthController controller = new AuthController(
            userService, mock(AuthenticationManager.class), new HttpSessionSecurityContextRepository(), verifier, userDetails);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private User linkedUser() {
        return User.builder().id(3L).email("g@vori.com").passwordHash("$2a$hash").googleSub("sub-1").role(Role.USER).build();
    }

    @Test
    void 구글_로그인_세션에는_비밀번호_해시가_남지_않는다() {
        User user = linkedUser();
        when(verifier.verify("tok")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("sub-1", "g@vori.com", "구글"));
        when(userService.findOrCreateGoogleUser("sub-1", "g@vori.com", "구글")).thenReturn(user);
        when(userDetails.loadUserByUsername("g@vori.com")).thenReturn(new UserPrincipal(user, false));

        controller.google(new GoogleLoginRequest("tok"), new MockHttpServletRequest(), new MockHttpServletResponse());

        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(3L, principal.getId());
        assertNull(principal.getPassword());
        verify(userService).recordLogin(3L);
    }

    @Test
    void 제재된_계정은_구글로도_로그인할_수_없다() {
        User user = linkedUser();
        when(verifier.verify("tok")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("sub-1", "g@vori.com", "구글"));
        when(userService.findOrCreateGoogleUser(any(), any(), any())).thenReturn(user);
        when(userDetails.loadUserByUsername("g@vori.com")).thenReturn(new UserPrincipal(user, true));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                controller.google(new GoogleLoginRequest("tok"), new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertEquals(403, e.getStatusCode().value());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void 첫_구글_로그인이_동시에_와서_중복_저장이_나면_한번_더_찾아_로그인한다() {
        User user = linkedUser();
        when(verifier.verify("tok")).thenReturn(new GoogleTokenVerifier.GoogleIdentity("sub-1", "g@vori.com", "구글"));
        when(userService.findOrCreateGoogleUser("sub-1", "g@vori.com", "구글"))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq_users_google_sub"))
                .thenReturn(user);
        when(userDetails.loadUserByUsername("g@vori.com")).thenReturn(new UserPrincipal(user, false));

        controller.google(new GoogleLoginRequest("tok"), new MockHttpServletRequest(), new MockHttpServletResponse());

        verify(userService, times(2)).findOrCreateGoogleUser("sub-1", "g@vori.com", "구글");
        assertEquals(3L, ((UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }
}
