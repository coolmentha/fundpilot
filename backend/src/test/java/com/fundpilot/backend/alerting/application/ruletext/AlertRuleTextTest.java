package com.fundpilot.backend.alerting.application.ruletext;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 提醒规则摘要的中文措辞：条件型给条件摘要，建议型带出种类与止盈参数。 */
class AlertRuleTextTest {

    @Test
    void 条件型规则摘要即条件说明() {
        AlertRule rule = AlertRule.create(1L, AlertRuleScope.GLOBAL, null, AlertRuleKind.CONDITION,
                ConditionGroup.allOf(List.of(
                        AlertCondition.of(IndicatorCode.DAILY_CHANGE, ConditionRelation.ABOVE,
                                new BigDecimal("0.05")),
                        AlertCondition.of(IndicatorCode.PRICE_VS_MA, ConditionRelation.BELOW,
                                new BigDecimal("0")))),
                null, true);

        assertThat(AlertRuleText.summarize(rule)).isEqualTo("当日涨跌幅 高于 0.05 且 净值与均线偏离率 低于 0");
    }

    @Test
    void 逻辑破坏止损在条件摘要前加种类() {
        AlertRule rule = AlertRule.create(1L, AlertRuleScope.FUND, 9L, AlertRuleKind.LOGIC_BROKEN,
                ConditionGroup.allOf(List.of(
                        AlertCondition.of(IndicatorCode.WEEKLY_MACD_HISTOGRAM, ConditionRelation.BELOW,
                                new BigDecimal("0")),
                        AlertCondition.of(IndicatorCode.WEEKLY_MACD_HISTOGRAM, ConditionRelation.DECREASING))),
                null, true);

        assertThat(AlertRuleText.summarize(rule)).startsWith("逻辑破坏止损：").contains("周线 MACD 柱高");
    }

    @Test
    void 回撤止盈摘要描述六个参数() {
        AlertRule rule = AlertRule.create(1L, AlertRuleScope.GLOBAL, null, AlertRuleKind.TRAILING_STOP, null,
                new TakeProfitParams(new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                        new BigDecimal("0.50"), new BigDecimal("0.20"), 10),
                true);

        assertThat(AlertRuleText.summarize(rule)).isEqualTo(
                "回撤止盈：盈利达 15% 后回撤 6% 即提醒，收割浮盈的 50%，最低保留 50%，单次最多卖 20%，冷静期 10 个交易日");
    }

    @Test
    void 比例展示去掉多余的零() {
        assertThat(AlertRuleText.percent(new BigDecimal("0.15"))).isEqualTo("15%");
        assertThat(AlertRuleText.percent(new BigDecimal("0.065"))).isEqualTo("6.5%");
        assertThat(AlertRuleText.percent(BigDecimal.ZERO)).isEqualTo("0%");
        assertThat(AlertRuleText.percent(BigDecimal.ONE)).isEqualTo("100%");
    }
}