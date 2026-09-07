package com.fundpilot.backend.accounting.infrastructure.migration.unitnavrebuild;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AccountingRebuildRunnerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AccountingRebuildService.class, () -> mock(AccountingRebuildService.class))
            .withUserConfiguration(AccountingRebuildRunner.class);

    @Test
    void validationModeDoesNotRegisterRebuildRunner() {
        runner.withPropertyValues("fundpilot.deployment.validation-mode=true")
                .run(context -> assertThat(context).doesNotHaveBean(AccountingRebuildRunner.class));
    }
}
