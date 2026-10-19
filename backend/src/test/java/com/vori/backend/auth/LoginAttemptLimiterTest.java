package com.vori.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 같은 이메일로 비밀번호를 연달아 틀리면 잠시 로그인 시도를 막는다(무차별 대입 방어). */
class LoginAttemptLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-19T00:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, Duration.ofMinutes(15), now::get);

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) limiter.recordFailure(email);
    }

    @Test
    void blocksAfterFiveFailures() {
        fail("a@vori.com", 4);
        assertThatCode(() -> limiter.check("a@vori.com")).doesNotThrowAnyException();
        fail("a@vori.com", 1);
        assertThatThrownBy(() -> limiter.check("A@Vori.com ")) // 대소문자·공백을 바꿔도 같은 계정
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(429);
        assertThatCode(() -> limiter.check("b@vori.com")).doesNotThrowAnyException();
    }

    @Test
    void unblocksAfterWindow() {
        fail("a@vori.com", 5);
        now.set(now.get().plus(Duration.ofMinutes(15)));
        assertThatCode(() -> limiter.check("a@vori.com")).doesNotThrowAnyException();
    }

    @Test
    void successResets() {
        fail("a@vori.com", 4);
        limiter.recordSuccess("a@vori.com");
        fail("a@vori.com", 4);
        assertThatCode(() -> limiter.check("a@vori.com")).doesNotThrowAnyException();
    }
}
