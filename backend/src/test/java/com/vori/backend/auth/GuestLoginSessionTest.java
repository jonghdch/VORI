package com.vori.backend.auth;

import com.vori.backend.guest.GuestAccountService;
import com.vori.backend.user.Role;
import com.vori.backend.user.User;
import com.vori.backend.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 「체험하기」도 구글 로그인처럼 ProviderManager 를 거치지 않고 세션을 만든다.
 * 만든 계정으로 바로 로그인되는지, 상한에 걸리면 세션 없이 끝나는지 본다.
 */
class GuestLoginSessionTest {

    private final UserService userService = mock(UserService.class);
    private final CustomUserDetailsService userDetails = mock(CustomUserDetailsService.class);
    private final GuestAccountService guestAccountService = mock(GuestAccountService.class);
    private final AuthController controller = new AuthController(
            userService, mock(AuthenticationManager.class), new HttpSessionSecurityContextRepository(),
            mock(GoogleTokenVerifier.class), userDetails,
            mock(com.vori.backend.user.AccountDeletionService.class), guestAccountService);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 체험_계정을_만들면_그_계정으로_바로_로그인된다() {
        User guest = User.builder().id(7L).email("guest-261019-0a1b2c3d@guest.vori.local").role(Role.USER).build();
        when(guestAccountService.create()).thenReturn(guest);
        when(userDetails.loadUserByUsername(guest.getEmail())).thenReturn(new UserPrincipal(guest, false));
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.guest(request, new MockHttpServletResponse());

        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(7L, principal.getId());
        assertNull(principal.getPassword());
        assertNotNull(request.getSession(false), "세션이 만들어져야 쿠키로 이어서 쓴다");
        verify(userService).recordLogin(7L);
    }

    @Test
    void 오늘_상한에_걸리면_세션을_만들지_않는다() {
        when(guestAccountService.create())
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "오늘 준비한 체험 계정을 다 썼어요."));
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                controller.guest(request, new MockHttpServletResponse()));

        assertEquals(429, e.getStatusCode().value());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession(false));
        verifyNoInteractions(userDetails);
    }
}
