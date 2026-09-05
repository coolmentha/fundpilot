package com.fundpilot.backend.identityaccess.infrastructure.gateway.authentication;

import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.identityaccess.infrastructure.configuration.IdentityAccessProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSessionTokenGatewayTest {

    @Test
    void tokenCarriesUserVersionForSessionInvalidation() {
        var gateway = gateway();

        var identity = gateway.parse(gateway.issue(7L, UserRole.USER, 4L)).orElseThrow();

        assertThat(identity.userId()).isEqualTo(7L);
        assertThat(identity.role()).isEqualTo(UserRole.USER);
        assertThat(identity.userVersion()).isEqualTo(4L);
    }

    @Test
    void tokenCanCarryInitialVersion() {
        var gateway = gateway();

        var identity = gateway.parse(gateway.issue(7L, UserRole.USER, 0L)).orElseThrow();

        assertThat(identity.userVersion()).isZero();
    }

    private HmacSessionTokenGateway gateway() {
        return new HmacSessionTokenGateway(
                new IdentityAccessProperties(null, "test-session-secret"),
                Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));
    }
}
