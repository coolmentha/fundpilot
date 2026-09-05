package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.application.gateway.authentication.LoginRateLimiter;
import com.fundpilot.backend.identityaccess.infrastructure.configuration.LoginRateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryLoginRateLimiterTest {

    @Test
    void normalizedSourceAndUsernameShareOneWindowAndOtherKeysRemainAvailable() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-04T00:00:00Z"));
        var limiter = limiter(clock, 2, Duration.ofSeconds(30), 20);

        assertThat(limiter.check(" 192.0.2.1 ", " alice ").allowed()).isTrue();
        assertThat(limiter.check("192.0.2.1", "alice").allowed()).isTrue();
        LoginRateLimiter.Decision limited = limiter.check("192.0.2.1", "alice");

        assertThat(limited.allowed()).isFalse();
        assertThat(limited.retryAfterSeconds()).isEqualTo(30);
        assertThat(limiter.check("192.0.2.2", "alice").allowed()).isTrue();
        assertThat(limiter.check("192.0.2.1", "bob").allowed()).isTrue();
    }

    @Test
    void windowExpiryRestoresAttemptsAndResetRemovesTheKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-04T00:00:00Z"));
        var limiter = limiter(clock, 1, Duration.ofSeconds(10), 20);

        assertThat(limiter.check("source", "alice").allowed()).isTrue();
        assertThat(limiter.check("source", "alice").allowed()).isFalse();
        clock.advance(Duration.ofSeconds(10));

        assertThat(limiter.check("source", "alice").allowed()).isTrue();
        limiter.reset("source", "alice");
        assertThat(limiter.trackedEntryCount()).isZero();
    }

    @Test
    void activeStateIsBoundedByConfiguredEntryLimit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-04T00:00:00Z"));
        var limiter = limiter(clock, 2, Duration.ofMinutes(1), 2);

        limiter.check("source-a", "alice");
        clock.advance(Duration.ofSeconds(1));
        limiter.check("source-b", "alice");
        clock.advance(Duration.ofSeconds(1));
        limiter.check("source-c", "alice");

        assertThat(limiter.trackedEntryCount()).isEqualTo(2);
    }

    private InMemoryLoginRateLimiter limiter(Clock clock, int maxAttempts, Duration window, int maxEntries) {
        return new InMemoryLoginRateLimiter(
                new LoginRateLimitProperties(maxAttempts, window, maxEntries, List.of()), clock);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        private void advance(Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }
    }
}
