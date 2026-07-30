package com.fundpilot.backend.platform.persistence.flyway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fundpilot.flyway.legacy-v7-repair")
public record LegacyV7FlywayRepairProperties(boolean enabled) {
}
