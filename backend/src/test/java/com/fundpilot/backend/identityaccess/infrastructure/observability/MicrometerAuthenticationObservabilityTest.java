package com.fundpilot.backend.identityaccess.infrastructure.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerAuthenticationObservabilityTest {

    @Test
    void recordsSeparateFailureLockoutAndAnomalousTrafficCounters() {
        var registry = new SimpleMeterRegistry();
        var observability = new MicrometerAuthenticationObservability(registry);

        observability.loginFailed("192.0.2.1", "alice", "invalid_credentials");
        observability.loginRateLimited("192.0.2.1", "alice", 30);
        observability.abnormalTraffic("192.0.2.1", "alice", "rate_limit");

        assertThat(registry.get("auth_login_failures_total")
                .tag("reason", "invalid_credentials").counter().count()).isEqualTo(1);
        assertThat(registry.get("auth_login_rate_limited_total").counter().count()).isEqualTo(1);
        assertThat(registry.get("auth_login_lockouts_total").counter().count()).isEqualTo(1);
        assertThat(registry.get("auth_login_anomalous_traffic_total")
                .tag("reason", "rate_limit").counter().count()).isEqualTo(1);
    }

    @Test
    void securityLogsDoNotContainPasswordOrRequestBody() {
        var registry = new SimpleMeterRegistry();
        var observability = new MicrometerAuthenticationObservability(registry);
        Logger logger = (Logger) LoggerFactory.getLogger(MicrometerAuthenticationObservability.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String password = "NeverLogThisPassword-123";
        try {
            observability.loginFailed("192.0.2.1", "alice", "invalid_credentials");
            observability.loginRateLimited("192.0.2.1", "alice", 30);
            observability.abnormalTraffic("192.0.2.1", "alice", "rate_limit");
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(password);
            assertThat(event.getFormattedMessage()).doesNotContain("requestBody");
            assertThat(event.getFormattedMessage()).doesNotContain("password");
        });
    }
}
