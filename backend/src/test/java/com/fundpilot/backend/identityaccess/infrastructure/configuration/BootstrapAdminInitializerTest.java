package com.fundpilot.backend.identityaccess.infrastructure.configuration;

import com.fundpilot.backend.identityaccess.application.command.useradministration.UserAdministrationCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BootstrapAdminInitializerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(UserAdministrationCommandHandler.class,
                    () -> mock(UserAdministrationCommandHandler.class))
            .withUserConfiguration(UserAuthConfiguration.class, BootstrapAdminInitializer.class);

    @Test
    void validationModeDoesNotRegisterBootstrapWriter() {
        runner.withPropertyValues("fundpilot.deployment.validation-mode=true")
                .run(context -> assertThat(context).doesNotHaveBean(BootstrapAdminInitializer.class));
    }
}
