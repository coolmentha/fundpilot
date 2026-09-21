package com.fundpilot.backend.alerting.domain.alertrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AlertRuleTest {

    @Test
    void 全局规则忽略传入的基金ID() {
        AlertRule rule = AlertRule.create(3L, AlertRuleScope.GLOBAL, 11L, AlertRuleType.RISE,
                new BigDecimal("0.05"), true);

        assertThat(rule.global()).isTrue();
        assertThat(rule.portfolioFundId()).isNull();
        assertThat(rule.scope()).isEqualTo(AlertRuleScope.GLOBAL);
    }

    @Test
    void 单基金规则必须指定基金() {
        assertThatIllegalArgumentException().isThrownBy(() -> AlertRule.create(3L, AlertRuleScope.FUND, null,
                AlertRuleType.RISE, new BigDecimal("0.05"), true));
        assertThatIllegalArgumentException().isThrownBy(() -> AlertRule.create(3L, AlertRuleScope.FUND, 0L,
                AlertRuleType.RISE, new BigDecimal("0.05"), true));
    }

    @Test
    void 阈值必须大于0且不超过1() {
        assertThatIllegalArgumentException().isThrownBy(() -> rule(new BigDecimal("0")));
        assertThatIllegalArgumentException().isThrownBy(() -> rule(new BigDecimal("-0.01")));
        assertThatIllegalArgumentException().isThrownBy(() -> rule(new BigDecimal("1.0001")));
        assertThatIllegalArgumentException().isThrownBy(() -> rule(null));
        assertThat(rule(BigDecimal.ONE).threshold()).isEqualByComparingTo("1");
    }

    @Test
    void 创建时非法用户ID被拒绝() {
        assertThatIllegalArgumentException().isThrownBy(() -> AlertRule.create(0L, AlertRuleScope.GLOBAL, null,
                AlertRuleType.RISE, new BigDecimal("0.05"), true));
    }

    @Test
    void 上涨与盈利达到阈值即触发() {
        AlertRule rise = rule(new BigDecimal("0.05"));
        assertThat(rise.triggered(new BigDecimal("0.05"))).isTrue();
        assertThat(rise.triggered(new BigDecimal("0.0501"))).isTrue();
        assertThat(rise.triggered(new BigDecimal("0.0499"))).isFalse();
        assertThat(rise.triggered(null)).isFalse();
    }

    @Test
    void 下跌跌破负阈值才触发() {
        AlertRule drop = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleType.DROP,
                new BigDecimal("0.05"), true);

        assertThat(drop.triggered(new BigDecimal("-0.05"))).isTrue();
        assertThat(drop.triggered(new BigDecimal("-0.08"))).isTrue();
        assertThat(drop.triggered(new BigDecimal("-0.0499"))).isFalse();
        assertThat(drop.triggered(new BigDecimal("0.06"))).isFalse();
    }

    @Test
    void 盈利类型只对持仓中的基金生效() {
        AlertRule profit = AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleType.PROFIT,
                new BigDecimal("0.10"), true);

        assertThat(profit.appliesTo(true)).isTrue();
        assertThat(profit.appliesTo(false)).isFalse();
        assertThat(rule(new BigDecimal("0.05")).appliesTo(false)).isTrue();
    }

    @Test
    void 更新可切换范围与阈值并支持启停() {
        AlertRule rule = AlertRule.rehydrate(9L, 3L, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE,
                new BigDecimal("0.05"), true);

        rule.update(AlertRuleScope.FUND, 11L, AlertRuleType.PROFIT, new BigDecimal("0.20"));
        assertThat(rule.scope()).isEqualTo(AlertRuleScope.FUND);
        assertThat(rule.portfolioFundId()).isEqualTo(11L);
        assertThat(rule.type()).isEqualTo(AlertRuleType.PROFIT);
        assertThat(rule.threshold()).isEqualByComparingTo("0.20");

        rule.disable();
        assertThat(rule.enabled()).isFalse();
        rule.enable();
        assertThat(rule.enabled()).isTrue();
    }

    private static AlertRule rule(BigDecimal threshold) {
        return AlertRule.create(3L, AlertRuleScope.GLOBAL, null, AlertRuleType.RISE, threshold, true);
    }
}
