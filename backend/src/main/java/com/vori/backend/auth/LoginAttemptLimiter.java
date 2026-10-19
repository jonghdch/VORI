package com.vori.backend.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 이메일 로그인 무차별 대입 방어. 같은 이메일로 비밀번호를 maxFailures 번 틀리면
 * 첫 실패부터 window 가 지날 때까지 로그인 시도를 429 로 막는다. 성공하면 센 것을 지운다.
 *
 * <p>메모리에만 센다 — 서버를 다시 띄우면 초기화된다. 서버 한 대 기준(PetChatService 의 횟수 제한과 같은 전제).
 */
@Component
public class LoginAttemptLimiter {

    private final int maxFailures;
    private final Duration window;
    private final Supplier<Instant> clock;

    /** 이메일별 (첫 실패 시각, 실패 횟수). */
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private record Failures(Instant since, int count) {}

    @Autowired
    public LoginAttemptLimiter(@Value("${auth.login.max-failures:5}") int maxFailures,
                               @Value("${auth.login.lock-minutes:15}") int lockMinutes) {
        this(maxFailures, Duration.ofMinutes(lockMinutes), Instant::now);
    }

    /** 테스트에서 시각을 고정하려고 분리했다. */
    LoginAttemptLimiter(int maxFailures, Duration window, Supplier<Instant> clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** 막힌 이메일이면 비밀번호를 확인하기 전에 429. */
    public void check(String email) {
        Failures f = current(key(email));
        if (f != null && f.count() >= maxFailures) {
            long minutes = Math.max(1, Duration.between(clock.get(), f.since().plus(window)).toMinutes() + 1);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도가 너무 많아요. " + minutes + "분 뒤에 다시 시도해 주세요.");
        }
    }

    public void recordFailure(String email) {
        Instant now = clock.get();
        failures.compute(key(email), (k, f) ->
                f == null || expired(f, now) ? new Failures(now, 1) : new Failures(f.since(), f.count() + 1));
    }

    public void recordSuccess(String email) {
        failures.remove(key(email));
    }

    private Failures current(String key) {
        Failures f = failures.get(key);
        if (f != null && expired(f, clock.get())) {
            failures.remove(key, f);
            return null;
        }
        return f;
    }

    private boolean expired(Failures f, Instant now) {
        return !now.isBefore(f.since().plus(window));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
