package com.fundpilot.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 候选发布阶段不注册定时任务，避免回滚窗口内产生不可保留的后台写入。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "fundpilot.deployment.validation-mode",
        havingValue = "false",
        matchIfMissing = true)
public class SchedulingConfig {
}
