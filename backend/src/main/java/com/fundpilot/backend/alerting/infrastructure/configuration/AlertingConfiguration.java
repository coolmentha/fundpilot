package com.fundpilot.backend.alerting.infrastructure.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlertingProperties.class)
public class AlertingConfiguration {
}
