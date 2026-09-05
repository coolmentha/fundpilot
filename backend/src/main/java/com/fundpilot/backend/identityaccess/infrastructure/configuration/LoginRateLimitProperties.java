package com.fundpilot.backend.identityaccess.infrastructure.configuration;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fundpilot.auth.login")
public record LoginRateLimitProperties(
        int maxAttempts,
        Duration window,
        int maxEntries,
        List<String> trustedProxies) {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    public LoginRateLimitProperties {
        maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
        window = window == null || window.isZero() || window.isNegative() ? DEFAULT_WINDOW : window;
        maxEntries = maxEntries > 0 ? maxEntries : DEFAULT_MAX_ENTRIES;
        trustedProxies = trustedProxies == null ? List.of() : trustedProxies.stream()
                .filter(proxy -> proxy != null && !proxy.isBlank())
                .map(String::trim)
                .toList();
    }
}
