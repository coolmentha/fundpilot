package com.fundpilot.backend.identityaccess.infrastructure.configuration;

import com.fundpilot.backend.identityaccess.application.command.useradministration.UserAdministrationCommandHandler;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@ConfigurationProperties("fundpilot.auth.bootstrap")
record BootstrapAdminProperties(String username, String password) {}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BootstrapAdminProperties.class)
class UserAuthConfiguration {}

@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final UserAdministrationCommandHandler users;
    private final BootstrapAdminProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()
                ) return;
        users.ensureBootstrapAdmin(properties.username(), properties.password());
    }
}
