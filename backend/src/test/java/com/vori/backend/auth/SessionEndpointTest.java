package com.vori.backend.auth;

import com.vori.backend.auth.dto.SessionResponse;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserService;
import com.vori.backend.user.dto.MeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** GET /api/auth/session 은 미로그인이어도 200(authenticated=false), /api/auth/me 는 여전히 401 이어야 한다. */
class SessionEndpointTest {

    private final UserService userService = mock(UserService.class);
    private final AuthController controller = new AuthController(
            userService, mock(AuthenticationManager.class), new HttpSessionSecurityContextRepository(),
            mock(GoogleTokenVerifier.class), mock(CustomUserDetailsService.class),
            mock(com.vori.backend.user.AccountDeletionService.class), mock(LoginAttemptLimiter.class),
            mock(com.vori.backend.guest.GuestAccountService.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 미로그인이면_200_본문으로_로그인_안_됨을_답한다() {
        SessionResponse res = controller.session();
        assertFalse(res.authenticated());
        assertNull(res.user());
        verifyNoInteractions(userService);
    }

    @Test
    void 익명_인증이어도_로그인_안_됨() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertFalse(controller.session().authenticated());
    }

    @Test
    void 로그인이면_사용자_정보를_준다() {
        User u = User.builder().id(7L).email("a@b.c").role(Role.USER).build();
        UserPrincipal principal = new UserPrincipal(u, false);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MeResponse me = MeResponse.from(u);
        when(userService.getMe(7L)).thenReturn(me);

        SessionResponse res = controller.session();

        assertTrue(res.authenticated());
        assertSame(me, res.user());
    }

    @Test
    void me_는_미로그인이면_여전히_401() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, controller::me);
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }
}
