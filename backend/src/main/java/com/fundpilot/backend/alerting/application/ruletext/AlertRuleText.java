package com.fundpilot.backend.alerting.application.ruletext;

import com.fundpilot.backend.alerting.application.condition.AlertConditionText;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 提醒规则的中文措辞：条件型规则给条件摘要，回撤止盈给六个参数的白话描述。
 *
 * <p>与 {@link AlertConditionText} 一样，中文文案集中在应用层；领域层只提供指标名与关系别名与参数取值。
 */
public final class AlertRuleText {

    private AlertRuleText() {
    }

    /** 规则摘要：条件型（含逻辑破坏止损）是条件组合，回撤止盈是参数描述。 */
    public static String summarize(AlertRule rule) {
        if (rule.kind() == AlertRuleKind.TRAILING_STOP) {
            return takeProfit(rule.takeProfit());
        }
        String conditions = AlertConditionText.summarize(rule.conditions());
        return rule.kind() == AlertRuleKind.LOGIC_BROKEN ? rule.kind().label() + "：" + conditions : conditions;
    }

    private static String takeProfit(TakeProfitParams params) {
        return params == null
                ? AlertRuleKind.TRAILING_STOP.label()
                : "回撤止盈：盈利达 " + percent(params.activation())
                + " 后回撤 " + percent(params.pullback())
                + " 即提醒，收割浮盈的 " + percent(params.harvest())
                + "，最低保留 " + percent(params.minimumHolding())
                + "，单次最多卖 " + percent(params.maxSingleSell())
                + "，冷静期 " + params.cooldownDays() + " 个交易日";
    }

    /** 比例的中文展示，如 0.15 → 15%。 */
    public static String percent(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }
}