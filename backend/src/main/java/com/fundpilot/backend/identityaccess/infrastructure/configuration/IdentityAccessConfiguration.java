package com.fundpilot.backend.identityaccess.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityAccessProperties.class)
public class IdentityAccessConfiguration {
}
