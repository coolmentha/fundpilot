package com.fundpilot.backend.controller;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller 层冒烟测试(issue #16):验证应用启动 + 6 个 Controller bean 全部注入成功。
 * <p>issue #16 明示本期不写端到端 MockMvc 测试,只验证启动 + 冒烟通。
 * 上下文加载成功即证明 25 个端点路径无冲突、所有依赖注入正常。
 */
class ControllerSmokeTest extends AbstractIntegrationTest {

    @Autowired ApplicationContext applicationContext;

    @Test
    void allControllersLoaded() {
        // 6 个 Controller 全部应作为 bean 加载(路径/依赖注入无误即通过)
        assertThat(applicationContext.getBean(
                com.fundpilot.backend.fund.controller.FundController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.fundpilot.backend.strategy.controller.StrategyController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.fundpilot.backend.signal.controller.SignalController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.fundpilot.backend.signal.controller.SignalOperationController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.fundpilot.backend.fund.controller.TransactionCancelController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.fundpilot.backend.user.controller.UserConfigController.class)).isNotNull();
        assertThat(applicationContext.getBean(
                com.fundpilot.backend.market.controller.MarketDataController.class)).isNotNull();
    }
}
