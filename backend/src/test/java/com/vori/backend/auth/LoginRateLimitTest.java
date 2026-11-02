package com.vori.backend.auth;

import com.vori.backend.auth.dto.LoginRequest;
import com.vori.backend.user.AccountDeletionService;
import com.vori.backend.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 비밀번호를 5번 틀린 뒤에는 인증을 시도하지도 않고 429. */
class LoginRateLimitTest {

    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(5, 20, 50, Duration.ofMinutes(15), () -> Instant.parse("2026-10-19T00:00:00Z"));
    private final AuthController controller = new AuthController(
            mock(UserService.class), authManager, new HttpSessionSecurityContextRepository(),
            mock(GoogleTokenVerifier.class), mock(CustomUserDetailsService.class), mock(AccountDeletionService.class),
            limiter);

    private int status(Runnable r) {
        return assertThrows(ResponseStatusException.class, r::run).getStatusCode().value();
    }

    @Test
    void sixthAttemptAfterFiveWrongPasswordsIs429() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        LoginRequest req = new LoginRequest("a@vori.com", "wrong");

        for (int i = 0; i < 5; i++) {
            assertEquals(401, status(() -> controller.login(req, new MockHttpServletRequest(), new MockHttpServletResponse())));
        }
        assertEquals(429, status(() -> controller.login(req, new MockHttpServletRequest(), new MockHttpServletResponse())));
        verify(authManager, times(5)).authenticate(any());
    }

    /** 동시에 몰려온 요청도 인증까지 가는 건 다섯 번뿐이다. */
    @Test
    void concurrentAttemptsCannotPassTheLimit() throws Exception {
        java.util.concurrent.CountDownLatch inAuth = new java.util.concurrent.CountDownLatch(1);
        when(authManager.authenticate(any())).thenAnswer(inv -> {
            inAuth.await(2, java.util.concurrent.TimeUnit.SECONDS); // 모두 인증 안에 머문 채로 겹치게 한다
            throw new BadCredentialsException("bad");
        });
        LoginRequest req = new LoginRequest("a@vori.com", "wrong");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(20);
        java.util.List<java.util.concurrent.Future<?>> calls = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            calls.add(pool.submit(() -> {
                try { controller.login(req, new MockHttpServletRequest(), new MockHttpServletResponse()); }
                catch (ResponseStatusException ignored) { }
            }));
        }
        Thread.sleep(300);
        inAuth.countDown();
        for (var c : calls) c.get();
        pool.shutdown();
        verify(authManager, times(5)).authenticate(any());
    }

    /** 제재 계정은 맞는 비밀번호로 여러 번 들어와도 429 가 아니라 제재 안내를 계속 본다. */
    @Test
    void suspendedAccountKeepsGetting403Not429() {
        when(authManager.authenticate(any())).thenThrow(new org.springframework.security.authentication.LockedException("locked"));
        LoginRequest req = new LoginRequest("banned@vori.com", "right");
        for (int i = 0; i < 8; i++) {
            assertEquals(403, status(() -> controller.login(req, new MockHttpServletRequest(), new MockHttpServletResponse())));
        }
    }
}
