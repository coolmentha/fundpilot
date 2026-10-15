package com.fundpilot.backend.alerting.application.ruletext;

import com.fundpilot.backend.alerting.application.condition.AlertConditionText;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.grid.GridParams;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 提醒规则的中文措辞：条件型规则给条件摘要，回撤止盈与网格给参数的白话描述。
 *
 * <p>与 {@link AlertConditionText} 一样，中文文案集中在应用层；领域层只提供指标名与关系别名与参数取值。
 */
public final class AlertRuleText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private AlertRuleText() {
    }

    /** 规则摘要：条件型（含逻辑破坏止损）是条件组合，无条件的种类是参数描述。 */
    public static String summarize(AlertRule rule) {
        if (rule.kind() == AlertRuleKind.TRAILING_STOP) {
            return takeProfit(rule.takeProfit());
        }
        if (rule.kind() == AlertRuleKind.GRID) {
            return grid(rule.grid());
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

    /** 网格摘要：以最近一笔成交净值为基准，上浮卖出、下降买入。 */
    public static String grid(GridParams params) {
        return params == null
                ? AlertRuleKind.GRID.label()
                : "网格交易：以最近一笔成交净值为基准，上浮 " + percent(params.upRatio())
                + " 提醒卖出，下降 " + percent(params.downRatio()) + " 提醒买入";
    }

    /** 比例的中文展示，如 0.15 → 15%。 */
    public static String percent(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }

    /** 带符号的比例展示，如 0.0523 → +5.23%、-0.03 → -3%；无值时给占位符。 */
    public static String signedPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        String text = percent(value);
        return value.signum() > 0 ? "+" + text : text;
    }

    /** 业务日期标签的中文展示（北京时间自然日）；无值时给占位符。 */
    public static String dateLabel(Instant dateLabel) {
        return dateLabel == null
                ? "-"
                : DATE.format(dateLabel.atZone(ZoneOffset.UTC).withZoneSameInstant(BusinessDay.ZONE));
    }
}
