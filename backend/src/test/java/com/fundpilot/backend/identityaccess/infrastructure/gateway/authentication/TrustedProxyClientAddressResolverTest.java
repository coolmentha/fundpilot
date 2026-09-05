package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.infrastructure.configuration.LoginRateLimitProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyClientAddressResolverTest {

    @Test
    void ignoresForwardedHeadersWhenNoTrustedProxyIsConfigured() {
        var resolver = resolver(List.of());

        assertThat(resolver.resolve("10.0.0.2", "198.51.100.7")).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve("deadbeef", "198.51.100.7")).isEqualTo("unknown");
    }

    @Test
    void walksForwardedChainOnlyWhenRemoteAddressIsTrusted() {
        var resolver = resolver(List.of("10.0.0.0/8"));

        assertThat(resolver.resolve("10.1.1.2", "198.51.100.7, 10.2.2.2"))
                .isEqualTo("198.51.100.7");
        assertThat(resolver.resolve("192.0.2.2", "198.51.100.7"))
                .isEqualTo("192.0.2.2");
    }

    private TrustedProxyClientAddressResolver resolver(List<String> trustedProxies) {
        return new TrustedProxyClientAddressResolver(
                new LoginRateLimitProperties(5, Duration.ofMinutes(1), 100, trustedProxies));
    }
}
