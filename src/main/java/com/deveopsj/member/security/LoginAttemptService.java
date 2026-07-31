package com.deveopsj.member.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final ConcurrentMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration blockDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login.block-duration:10m}") String blockDuration) {
        this(maxAttempts, DurationStyle.detectAndParse(blockDuration), Clock.systemUTC());
    }

    LoginAttemptService(int maxAttempts, Duration blockDuration, Clock clock) {
        if (maxAttempts < 1 || blockDuration.isZero() || blockDuration.isNegative()) {
            throw new IllegalArgumentException("로그인 차단 설정값을 확인해 주세요.");
        }
        this.maxAttempts = maxAttempts;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    public boolean isBlocked(String loginId, String remoteAddress) {
        String key = key(loginId, remoteAddress);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (!clock.instant().isBefore(attempt.lastAttemptAt().plus(blockDuration))) {
            attempts.remove(key, attempt);
            return false;
        }
        if (attempt.blockedUntil() == null) {
            return false;
        }
        if (!clock.instant().isBefore(attempt.blockedUntil())) {
            attempts.remove(key, attempt);
            return false;
        }
        return true;
    }

    public void loginFailed(String loginId, String remoteAddress) {
        if (attempts.size() >= MAX_TRACKED_KEYS) {
            removeExpiredEntries();
            if (attempts.size() >= MAX_TRACKED_KEYS) {
                return;
            }
        }
        Instant now = clock.instant();
        attempts.compute(key(loginId, remoteAddress), (key, current) -> {
            int failures = current == null ? 1 : current.failures() + 1;
            Instant blockedUntil = failures >= maxAttempts ? now.plus(blockDuration) : null;
            return new Attempt(failures, blockedUntil, now);
        });
    }

    public void loginSucceeded(String loginId, String remoteAddress) {
        attempts.remove(key(loginId, remoteAddress));
    }

    private void removeExpiredEntries() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().lastAttemptAt().plus(blockDuration)));
    }

    private String key(String loginId, String remoteAddress) {
        String id = loginId == null ? "" : loginId.strip().toLowerCase(Locale.ROOT);
        String address = remoteAddress == null ? "" : remoteAddress;
        return address + '\n' + id;
    }

    private record Attempt(int failures, Instant blockedUntil, Instant lastAttemptAt) {
    }
}
