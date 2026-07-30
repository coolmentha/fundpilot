package com.fundpilot.backend.platform.persistence.flyway;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LegacyV7FlywayRepairProperties.class)
@RequiredArgsConstructor
public class FlywayMigrationConfig {

    private final LegacyV7FlywayRepairService legacyV7FlywayRepairService;

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return legacyV7FlywayRepairService::migrate;
    }
}
