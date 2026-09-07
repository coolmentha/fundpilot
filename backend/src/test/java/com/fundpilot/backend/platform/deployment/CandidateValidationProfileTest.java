package com.fundpilot.backend.platform.deployment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateValidationProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=validation");

    @Test
    void validationProfileDisablesMigrationsAndStartupEventReplay() {
        runner.run(context -> {
            assertThat(context.getEnvironment().getProperty("fundpilot.deployment.validation-mode", Boolean.class))
                    .isTrue();
            assertThat(context.getEnvironment().getProperty("spring.flyway.enabled", Boolean.class))
                    .isFalse();
            assertThat(context.getEnvironment().getProperty(
                    "spring.modulith.events.republish-outstanding-events-on-restart", Boolean.class))
                    .isFalse();
        });
    }
}
