package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.LoginRateLimiter;
import com.fundpilot.backend.identityaccess.infrastructure.configuration.LoginRateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    // ponytail: per-process state is sufficient for one backend node; use Redis before horizontal scaling.
    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final Map<Key, AttemptWindow> windows = new HashMap<>();

    @Override
    public Decision check(String source, String normalizedUsername) {
        Key key = new Key(normalize(source), normalize(normalizedUsername));
        Instant now = clock.instant();
        synchronized (windows) {
            removeExpired(now);
            AttemptWindow window = windows.get(key);
            if (window == null) {
                evictIfNecessary();
                window = new AttemptWindow(now.plus(properties.window()), now, 0);
                windows.put(key, window);
            }
            window.lastTouchedAt = now;
            if (window.attempts >= properties.maxAttempts()) {
                return new Decision(false, retryAfterSeconds(window.resetAt, now));
            }
            window.attempts++;
            return new Decision(true, 0);
        }
    }

    @Override
    public void reset(String source, String normalizedUsername) {
        synchronized (windows) {
            windows.remove(new Key(normalize(source), normalize(normalizedUsername)));
        }
    }

    int trackedEntryCount() {
        synchronized (windows) {
            return windows.size();
        }
    }

    private void removeExpired(Instant now) {
        Iterator<Map.Entry<Key, AttemptWindow>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().getValue().resetAt)) {
                iterator.remove();
            }
        }
    }

    private void evictIfNecessary() {
        while (windows.size() >= properties.maxEntries()) {
            Key oldest = null;
            Instant oldestTouchedAt = null;
            for (Map.Entry<Key, AttemptWindow> entry : windows.entrySet()) {
                if (oldestTouchedAt == null || entry.getValue().lastTouchedAt.isBefore(oldestTouchedAt)) {
                    oldest = entry.getKey();
                    oldestTouchedAt = entry.getValue().lastTouchedAt;
                }
            }
            if (oldest == null) {
                return;
            }
            windows.remove(oldest);
        }
    }

    private long retryAfterSeconds(Instant resetAt, Instant now) {
        Duration remaining = Duration.between(now, resetAt);
        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        return Math.max(1, seconds);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record Key(String source, String username) {
    }

    private static final class AttemptWindow {
        private final Instant resetAt;
        private Instant lastTouchedAt;
        private int attempts;

        private AttemptWindow(Instant resetAt, Instant lastTouchedAt, int attempts) {
            this.resetAt = resetAt;
            this.lastTouchedAt = lastTouchedAt;
            this.attempts = attempts;
        }
    }
}
