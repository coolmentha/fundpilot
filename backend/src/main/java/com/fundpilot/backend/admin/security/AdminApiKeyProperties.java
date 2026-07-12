package com.fundpilot.backend.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fundpilot.admin")
public record AdminApiKeyProperties(String apiKey) {
}
