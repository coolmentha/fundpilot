package com.fundpilot.backend.alerting.application.command.rulemanagement;

import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler.RuleInput;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.AlertConditionMatch;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.util.Collection;
import java.util.Locale;

/**
 * 校验通过的规则输入：作用范围、目标基金、规则种类与种类对应的判定配置。
 *
 * <p>规则写入与「保存前试算」共用这一套校验，保证预览看到的口径与保存后的口径完全一致。
 * 条件型规则（含逻辑破坏止损）必须给条件组、不给止盈参数；回撤止盈必须给六个止盈参数、不给条件组。
 */
public record AlertRuleDraft(AlertRuleScope scope, Long portfolioFundId, AlertRuleKind kind,
                             ConditionGroup conditions, TakeProfitParams takeProfit) {

    /** 校验并翻译请求；{@code trackedFundIds} 是该用户当前关注的组合基金 ID。 */
    public static AlertRuleDraft validate(RuleInput input, Collection<Long> trackedFundIds) {
        AlertRuleScope scope = scope(input);
        AlertRuleKind kind = kind(input);
        return new AlertRuleDraft(scope, targetFundId(input, scope, trackedFundIds), kind,
                kind.needsConditions() ? conditions(input) : null,
                kind == AlertRuleKind.TRAILING_STOP ? takeProfit(input.takeProfit()) : null);
    }

    private static Long targetFundId(RuleInput input, AlertRuleScope scope, Collection<Long> trackedFundIds) {
        if (scope == AlertRuleScope.GLOBAL) {
            if (input.portfolioFundId() != null) {
                throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "全局规则不能指定基金");
            }
            return null;
        }
        if (input.portfolioFundId() == null || input.portfolioFundId() <= 0) {
            throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "单基金规则必须指定基金");
        }
        if (!trackedFundIds.contains(input.portfolioFundId())) {
            throw new BusinessException(ErrorCode.ALERT_RULE_TARGET_FUND_INVALID, "指定基金不在当前关注列表中");
        }
        return input.portfolioFundId();
    }

    private static AlertRuleScope scope(RuleInput input) {
        if (input.scope() == null || input.scope().isBlank()) {
            throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "提醒范围不能为空");
        }
        try {
            return AlertRuleScope.valueOf(input.scope().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "不支持的提醒范围");
        }
    }

    /** 规则种类；存量客户端不传时按通用条件规则处理。 */
    private static AlertRuleKind kind(RuleInput input) {
        if (input.kind() == null || input.kind().isBlank()) {
            return AlertRuleKind.CONDITION;
        }
        try {
            return AlertRuleKind.valueOf(input.kind().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ALERT_RULE_KIND_INVALID, "不支持的提醒规则种类");
        }
    }

    /** 把请求里的条件数组翻译为领域条件组；任何一项不合法都返回同一个错误码。 */
    private static ConditionGroup conditions(RuleInput input) {
        if (input.conditions() == null || input.conditions().isEmpty()) {
            throw new BusinessException(ErrorCode.ALERT_RULE_CONDITION_INVALID, "提醒规则至少需要一条条件");
        }
        try {
            return new ConditionGroup(match(input.match()), input.conditions().stream()
                    .map(AlertRuleDraft::condition).toList());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ALERT_RULE_CONDITION_INVALID, exception.getMessage());
        }
    }

    /** 回撤止盈的六个参数；缺少任一项或取值越界都按参数非法处理。 */
    private static TakeProfitParams takeProfit(RuleInput.TakeProfitInput input) {
        if (input == null) {
            throw new BusinessException(ErrorCode.ALERT_RULE_PARAMETER_INVALID, "回撤止盈必须配置止盈参数");
        }
        try {
            return new TakeProfitParams(input.activation(), input.pullback(), input.harvest(),
                    input.minimumHolding(), input.maxSingleSell(),
                    input.cooldownDays() == null ? 0 : input.cooldownDays());
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ALERT_RULE_PARAMETER_INVALID, exception.getMessage());
        }
    }

    private static AlertConditionMatch match(String value) {
        return value == null || value.isBlank()
                ? AlertConditionMatch.ALL
                : AlertConditionMatch.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static AlertCondition condition(RuleInput.ConditionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("条件不能为空");
        }
        if (input.indicator() == null || input.indicator().isBlank()) {
            throw new IllegalArgumentException("条件指标不能为空");
        }
        if (input.relation() == null || input.relation().isBlank()) {
            throw new IllegalArgumentException("条件关系不能为空");
        }
        return new AlertCondition(IndicatorCode.of(input.indicator().trim().toUpperCase(Locale.ROOT)),
                input.params(), ConditionRelation.valueOf(input.relation().trim().toUpperCase(Locale.ROOT)),
                input.value());
    }
}