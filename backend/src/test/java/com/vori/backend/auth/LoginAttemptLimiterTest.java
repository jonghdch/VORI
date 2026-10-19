package com.vori.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 같은 이메일로 비밀번호를 연달아 틀리면 잠시 로그인 시도를 막는다(무차별 대입 방어). */
class LoginAttemptLimiterTest {

    private static final String IP = "203.0.113.1";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-19T00:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, 20, 50, Duration.ofMinutes(15), now::get);

    /** 시도를 times 번 하고 모두 틀린 것으로 둔다(acquire 가 시도를 센다). */
    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) limiter.acquire(email, ip);
    }

    private int status(Runnable r) {
        return ((ResponseStatusException) org.assertj.core.api.Assertions.catchThrowable(r::run))
                .getStatusCode().value();
    }

    @Test
    void blocksAfterFiveFailures() {
        fail("a@vori.com", IP, 5);
        assertThat(status(() -> limiter.acquire("A@Vori.com ", IP))).isEqualTo(429); // 대소문자·공백을 바꿔도 같은 계정
        assertThatCode(() -> limiter.acquire("b@vori.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void unblocksAfterWindow() {
        fail("a@vori.com", IP, 5);
        now.set(now.get().plus(Duration.ofMinutes(15)));
        assertThatCode(() -> limiter.acquire("a@vori.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void successResets() {
        fail("a@vori.com", IP, 4);
        limiter.recordSuccess("a@vori.com", IP);
        fail("a@vori.com", IP, 4);
        assertThatCode(() -> limiter.acquire("a@vori.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void otherIpCannotLockOutTheOwner() {
        // 남이 피해자 이메일로 다섯 번 틀려도, 다른 곳에서 접속한 본인은 막히지 않는다
        fail("victim@vori.com", "198.51.100.9", 5);
        assertThatCode(() -> limiter.acquire("victim@vori.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void oneIpCannotSprayManyEmails() {
        for (int i = 0; i < 50; i++) limiter.acquire("user" + i + "@vori.com", IP);
        assertThat(status(() -> limiter.acquire("fresh@vori.com", IP))).isEqualTo(429);
        assertThatCode(() -> limiter.acquire("fresh@vori.com", "198.51.100.9")).doesNotThrowAnyException();
    }

    @Test
    void expiredEntriesAreSweptEvenIfNeverLookedUpAgain() {
        for (int i = 0; i < 40; i++) limiter.acquire("random" + i + "@vori.com", IP);
        assertThat(limiter.trackedKeys()).isGreaterThan(40);
        now.set(now.get().plus(Duration.ofMinutes(16)));
        limiter.acquire("later@vori.com", "198.51.100.9");
        assertThat(limiter.trackedKeys()).isEqualTo(3); // later 의 계정·이메일·IP 키만 남는다
    }

    @Test
    void rotatingIpsCannotGuessOneAccountForever() {
        // IP 를 바꿔 가며 한 계정에 대입해도 이메일 기준 20번에서 막힌다
        for (int i = 0; i < 20; i++) limiter.acquire("victim@vori.com", "198.51.100." + i);
        assertThat(status(() -> limiter.acquire("victim@vori.com", "192.0.2.77"))).isEqualTo(429);
    }

    @Test
    void releaseGivesBackTheAttempt() {
        // 제재 계정·서버 오류처럼 비밀번호를 틀린 게 아니면 센 것을 돌려준다
        for (int i = 0; i < 10; i++) {
            limiter.acquire("banned@vori.com", IP);
            limiter.release("banned@vori.com", IP);
        }
        assertThatCode(() -> limiter.acquire("banned@vori.com", IP)).doesNotThrowAnyException();
    }

    @Test
    void lockMessageRoundsRemainingMinutesUp() {
        fail("a@vori.com", IP, 5);
        ResponseStatusException e = (ResponseStatusException)
                org.assertj.core.api.Assertions.catchThrowable(() -> limiter.acquire("a@vori.com", IP));
        assertThat(e.getReason()).contains("15분");
        now.set(now.get().plus(Duration.ofMinutes(14)).plusSeconds(30));
        e = (ResponseStatusException)
                org.assertj.core.api.Assertions.catchThrowable(() -> limiter.acquire("a@vori.com", IP));
        assertThat(e.getReason()).contains("1분");
    }
}
