package com.fundpilot.backend.alerting.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 价格提醒模块配置；<b>不</b>承载 SMTP 凭据，后者由 {@code spring.mail.*} 提供。 */
@ConfigurationProperties("fundpilot.alerting")
public record AlertingProperties(boolean enabled, String evaluationCron, String mailFrom, String appBaseUrl) {

    public AlertingProperties {
        if (evaluationCron == null || evaluationCron.isBlank()) {
            evaluationCron = "0 30 14 * * MON-FRI";
        }
        if (appBaseUrl == null) {
            appBaseUrl = "";
        }
    }
}
