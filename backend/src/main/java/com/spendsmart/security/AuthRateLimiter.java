package com.spendsmart.security;

import com.spendsmart.common.ApiException;
import com.spendsmart.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window limiter for sign-in and registration.
 * Limits: N failed sign-ins per email, 5×N per client IP, N failed password re-confirmations per account,
 * and M registrations per IP per hour.
 * Single-instance only — a multi-instance deployment needs a shared store (documented in SECURITY.md).
 */
@Component
public class AuthRateLimiter {

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private final AppProperties.Security config;
    private final Clock clock;

    public AuthRateLimiter(AppProperties properties, Clock clock) {
        this.config = properties.security();
        this.clock = clock;
    }

    public void checkLogin(String email, String ip) {
        Duration window = config.loginLockout();
        check("login-email:" + email, config.loginMaxFailures(), window);
        check("login-ip:" + ip, config.loginMaxFailures() * 5, window);
    }

    public void recordLoginFailure(String email, String ip) {
        record("login-email:" + email);
        record("login-ip:" + ip);
    }

    public void recordLoginSuccess(String email) {
        events.remove("login-email:" + email);
    }

    /** Re-authentication inside a session (password change, email change, account deletion). */
    public void checkPasswordConfirmation(UUID userId) {
        check("reauth-user:" + userId, config.loginMaxFailures(), config.loginLockout());
    }

    public void recordPasswordConfirmationFailure(UUID userId) {
        record("reauth-user:" + userId);
    }

    public void checkAndRecordRegistration(String ip) {
        String key = "register-ip:" + ip;
        check(key, config.registrationPerIpPerHour(), Duration.ofHours(1));
        record(key);
    }

    private void check(String key, int limit, Duration window) {
        Deque<Instant> deque = events.get(key);
        if (deque == null) {
            return;
        }
        Instant cutoff = clock.instant().minus(window);
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                long retryAfter = Math.max(1, Duration.between(clock.instant(), deque.peekFirst().plus(window)).toSeconds());
                throw new RateLimitedException(retryAfter);
            }
        }
    }

    private void record(String key) {
        if (events.size() > MAX_TRACKED_KEYS) {
            events.clear(); // bounded memory under a flood of unique keys; limits restart rather than grow unbounded
        }
        Deque<Instant> deque = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(clock.instant());
        }
    }

    public static class RateLimitedException extends ApiException {
        private final long retryAfterSeconds;

        RateLimitedException(long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many attempts. Try again in " + Math.max(1, (retryAfterSeconds + 59) / 60) + " minute(s).");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
