package com.fundpilot.backend.investmentplan.adapter.scheduler.planexecution;

import com.fundpilot.backend.investmentplan.application.command.planexecution.InvestmentPlanExecutionCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanQueryHandler;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 定投执行任务此前挂在 {@code fundpilot.investment-plan-scheduler.enabled} 开关上,而该开关
 * 的 false 分支声称「回切 legacy 调度器」——legacy 调度器已不存在,置 false 等于静默停掉全部定投。
 * 开关已删除,本测试锁定「任务恒注册」与「配置项已移除」两个事实,防止再次引入静默停用分支。
 */
class InvestmentPlanExecutionJobRegistrationTest {

    @Test
    void 定投执行任务不依赖任何开关属性() {
        new ApplicationContextRunner()
                .withUserConfiguration(InvestmentPlanExecutionJob.class)
                .withBean(InvestmentPlanQueryHandler.class, () -> mock(InvestmentPlanQueryHandler.class))
                .withBean(InvestmentPlanExecutionCommandHandler.class,
                        () -> mock(InvestmentPlanExecutionCommandHandler.class))
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> assertThat(context).hasSingleBean(InvestmentPlanExecutionJob.class));
    }

    @Test
    void 回切开关配置项已移除() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context.getEnvironment()
                        .getProperty("fundpilot.investment-plan-scheduler.enabled")).isNull());
    }
}