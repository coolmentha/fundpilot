package com.fundpilot.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void normalModeRegistersScheduling() {
        runner.run(context -> assertThat(context).hasBean(
                TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void deploymentValidationModeDoesNotRegisterScheduling() {
        runner.withPropertyValues("fundpilot.deployment.validation-mode=true")
                .run(context -> assertThat(context).doesNotHaveBean(
                        TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
}
