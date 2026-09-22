package com.fundpilot.backend.insights.infrastructure.migration.returnsnapshotrebuild;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PortfolioReturnSnapshotRebuildRunnerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(PortfolioReturnSnapshotRebuildService.class,
                    () -> mock(PortfolioReturnSnapshotRebuildService.class))
            .withUserConfiguration(PortfolioReturnSnapshotRebuildRunner.class);

    @Test
    void registersRebuildRunnerByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(PortfolioReturnSnapshotRebuildRunner.class));
    }

    @Test
    void validationModeDoesNotRegisterRebuildRunner() {
        runner.withPropertyValues("fundpilot.deployment.validation-mode=true")
                .run(context -> assertThat(context).doesNotHaveBean(PortfolioReturnSnapshotRebuildRunner.class));
    }
}
