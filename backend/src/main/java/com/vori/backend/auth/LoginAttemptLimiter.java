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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 이메일 로그인 무차별 대입 방어. 비밀번호를 확인하기 전에 {@link #acquire} 로 시도를 하나 세고,
 * 성공하면 {@link #recordSuccess} 로 지운다. 틀린 시도는 센 그대로 남는다.
 *
 * <p>두 가지 기준으로 센다. 첫 시도부터 window 가 지날 때까지,
 * <ul>
 *   <li>같은 이메일 + 같은 IP 로 maxPerAccount 번 — 남이 다른 곳에서 틀려도 본인은 잠기지 않는다</li>
 *   <li>같은 IP 에서 이메일 상관없이 maxPerIp 번 — 한 곳에서 여러 계정을 대입하는 것을 막는다</li>
 * </ul>
 * 세기와 한도 확인을 한 번에(compute) 하므로 요청이 동시에 몰려도 한도를 넘겨 인증까지 가지 못한다.
 *
 * <p>메모리에만 센다 — 서버를 다시 띄우면 초기화된다. 서버 한 대 기준(PetChatService 의 횟수 제한과 같은 전제).
 * 만료된 항목은 1분에 한 번 통째로 지워, 다시 조회되지 않는 이메일이 쌓이지 않게 한다.
 */
@Component
public class LoginAttemptLimiter {

    private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

    private final int maxPerAccount;
    private final int maxPerIp;
    private final Duration window;
    private final Supplier<Instant> clock;

    /** 키별 (첫 시도 시각, 시도 횟수). 키는 "a:이메일|IP" 또는 "i:IP". */
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep;

    private record Attempts(Instant since, int count) {}

    @Autowired
    public LoginAttemptLimiter(@Value("${auth.login.max-failures:5}") int maxPerAccount,
                               @Value("${auth.login.max-failures-per-ip:50}") int maxPerIp,
                               @Value("${auth.login.lock-minutes:15}") int lockMinutes) {
        this(maxPerAccount, maxPerIp, Duration.ofMinutes(lockMinutes), Instant::now);
    }

    /** 테스트에서 시각을 고정하려고 분리했다. */
    LoginAttemptLimiter(int maxPerAccount, int maxPerIp, Duration window, Supplier<Instant> clock) {
        this.maxPerAccount = maxPerAccount;
        this.maxPerIp = maxPerIp;
        this.window = window;
        this.clock = clock;
        this.lastSweep = new AtomicReference<>(clock.get());
    }

    /** 시도를 하나 센다. 이미 한도에 닿았으면 세지 않고 429 — 비밀번호 확인 전에 부른다. */
    public void acquire(String email, String ip) {
        Instant now = clock.get();
        sweepIfDue(now);
        String ipKey = ipKey(ip);
        if (!take(ipKey, maxPerIp, now)) throw tooMany(ipKey, now);
        String accountKey = accountKey(email, ip);
        if (!take(accountKey, maxPerAccount, now)) {
            giveBack(ipKey); // 계정 쪽에서 막혔으면 IP 쪽에 센 것은 돌려준다
            throw tooMany(accountKey, now);
        }
    }

    /** 로그인에 성공하면 그 계정에 센 것을 지우고, IP 쪽에 센 이번 시도는 돌려준다. */
    public void recordSuccess(String email, String ip) {
        attempts.remove(accountKey(email, ip));
        giveBack(ipKey(ip));
    }

    /** 지금 세고 있는 키 수. 테스트용. */
    int trackedKeys() {
        return attempts.size();
    }

    /** 한도 안이면 하나 세고 true. 한도에 닿았으면 그대로 두고 false. 확인과 세기가 한 번에 일어난다. */
    private boolean take(String key, int max, Instant now) {
        boolean[] taken = {false};
        attempts.compute(key, (k, a) -> {
            if (a == null || expired(a, now)) {
                taken[0] = true;
                return new Attempts(now, 1);
            }
            if (a.count() >= max) return a;
            taken[0] = true;
            return new Attempts(a.since(), a.count() + 1);
        });
        return taken[0];
    }

    private void giveBack(String key) {
        attempts.computeIfPresent(key, (k, a) -> a.count() <= 1 ? null : new Attempts(a.since(), a.count() - 1));
    }

    private ResponseStatusException tooMany(String key, Instant now) {
        Attempts a = attempts.get(key);
        Instant until = a == null ? now : a.since().plus(window);
        long minutes = Math.max(1, Duration.between(now, until).toMinutes() + 1);
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "로그인 시도가 너무 많아요. " + minutes + "분 뒤에 다시 시도해 주세요.");
    }

    private void sweepIfDue(Instant now) {
        Instant last = lastSweep.get();
        if (now.isBefore(last.plus(SWEEP_EVERY)) || !lastSweep.compareAndSet(last, now)) return;
        attempts.values().removeIf(a -> expired(a, now));
    }

    private boolean expired(Attempts a, Instant now) {
        return !now.isBefore(a.since().plus(window));
    }

    private static String accountKey(String email, String ip) {
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "a:" + e + "|" + (ip == null ? "" : ip);
    }

    private static String ipKey(String ip) {
        return "i:" + (ip == null ? "" : ip);
    }
}
