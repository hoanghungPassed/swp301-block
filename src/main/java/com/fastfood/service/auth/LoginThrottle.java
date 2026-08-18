package com.fastfood.service.auth;

import com.fastfood.config.AppConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginThrottle {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_LOCK_MINUTES = 15;
    private static final int DEFAULT_WINDOW_MINUTES = 15;

    private static final LoginThrottle INSTANCE = new LoginThrottle();

    public static LoginThrottle getInstance() {
        return INSTANCE;
    }

    private final Map<String, Attempts> byKey = new ConcurrentHashMap<>();

    LoginThrottle() {
    }

    public Duration lockRemaining(String email, String clientIp) {
        Attempts a = byKey.get(key(email, clientIp));
        if (a == null || a.lockedUntil == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isBefore(a.lockedUntil) ? Duration.between(now, a.lockedUntil) : null;
    }

    public boolean isLocked(String email, String clientIp) {
        return lockRemaining(email, clientIp) != null;
    }

    public boolean recordFailure(String email, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        Attempts updated = byKey.compute(key(email, clientIp), (k, current) -> {
            Attempts a = (current == null || current.isStale(now)) ? new Attempts() : current;
            a.count++;
            a.lastFailureAt = now;
            if (a.count >= maxAttempts() && a.lockedUntil == null) {
                a.lockedUntil = now.plusMinutes(lockMinutes());
                a.justLocked = true;
            } else {
                a.justLocked = false;
            }
            return a;
        });
        purgeStale(now);
        return updated.justLocked;
    }

    public void recordSuccess(String email, String clientIp) {
        byKey.remove(key(email, clientIp));
    }

    void clear() {
        byKey.clear();
    }

    private String key(String email, String clientIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        return normalizedEmail + "|" + (clientIp == null ? "" : clientIp);
    }

    private void purgeStale(LocalDateTime now) {
        if (byKey.size() < 1000) {
            return;
        }
        byKey.values().removeIf(a -> a.isStale(now));
    }

    private int maxAttempts() {
        return AppConfig.getInt("security.login.maxAttempts", DEFAULT_MAX_ATTEMPTS);
    }

    private int lockMinutes() {
        return AppConfig.getInt("security.login.lockMinutes", DEFAULT_LOCK_MINUTES);
    }

    private int windowMinutes() {
        return AppConfig.getInt("security.login.windowMinutes", DEFAULT_WINDOW_MINUTES);
    }

    private final class Attempts {
        private int count;
        private LocalDateTime lastFailureAt;
        private LocalDateTime lockedUntil;
        private boolean justLocked;

        private boolean isStale(LocalDateTime now) {
            if (lockedUntil != null && now.isBefore(lockedUntil)) {
                return false;
            }
            return lastFailureAt == null || lastFailureAt.plusMinutes(windowMinutes()).isBefore(now);
        }
    }
}
