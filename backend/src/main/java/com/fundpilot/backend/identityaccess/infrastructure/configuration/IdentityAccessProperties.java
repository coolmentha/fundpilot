package com.fundpilot.backend.identityaccess.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fundpilot.admin")
public record IdentityAccessProperties(String apiKey, String sessionSecret) {
}
