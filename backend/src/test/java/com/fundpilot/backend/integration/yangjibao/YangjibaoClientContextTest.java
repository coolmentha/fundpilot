package com.fundpilot.backend.integration.yangjibao;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class YangjibaoClientContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class))
            .withUserConfiguration(YangjibaoClient.class, YangjibaoSigner.class)
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withPropertyValues(
                    "fundpilot.yangjibao.base-url=https://example.test",
                    "fundpilot.yangjibao.secret=test-secret");

    @Test
    void clientIsCreatedBySpringContext() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(YangjibaoClient.class));
    }
}
