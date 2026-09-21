package com.fundpilot.backend.alerting.application.command.rulemanagement;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提醒规则的写操作；所有访问都先校验归属，保证多用户隔离。 */
@Service
@RequiredArgsConstructor
public class AlertRuleCommandHandler {

    private final AlertRuleRepository rules;
    private final AlertFundFactsGateway facts;
    private final AlertRuleQueryHandler views;

    @Transactional
    public AlertRuleQueryHandler.RuleViewResult create(long ownerId, RuleInput input) {
        AlertRuleScope scope = scope(input);
        Long targetFundId = targetFundId(input, ownerId, scope);
        AlertRule rule;
        try {
            rule = AlertRule.create(ownerId, scope, targetFundId, type(input), input.threshold(),
                    input.enabled() == null || input.enabled());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ALERT_RULE_THRESHOLD_INVALID, exception.getMessage());
        }
        return views.viewOf(ownerId, rules.save(rule));
    }

    @Transactional
    public AlertRuleQueryHandler.RuleViewResult update(long ownerId, long ruleId, RuleInput input) {
        AlertRule rule = owned(ownerId, ruleId);
        AlertRuleScope scope = scope(input);
        Long targetFundId = targetFundId(input, ownerId, scope);
        try {
            rule.update(scope, targetFundId, type(input), input.threshold());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ALERT_RULE_THRESHOLD_INVALID, exception.getMessage());
        }
        setEnabled(rule, input.enabled());
        return views.viewOf(ownerId, rules.save(rule));
    }

    @Transactional
    public AlertRuleQueryHandler.RuleViewResult changeEnabled(long ownerId, long ruleId, boolean enabled) {
        AlertRule rule = owned(ownerId, ruleId);
        setEnabled(rule, enabled);
        return views.viewOf(ownerId, rules.save(rule));
    }

    @Transactional
    public void delete(long ownerId, long ruleId) {
        rules.softDelete(owned(ownerId, ruleId).id());
    }

    private AlertRule owned(long ownerId, long ruleId) {
        return rules.findById(ruleId)
                .filter(rule -> rule.ownerId() == ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_RULE_NOT_FOUND, "提醒规则不存在"));
    }

    private static void setEnabled(AlertRule rule, Boolean enabled) {
        if (enabled == null) {
            return;
        }
        if (enabled) {
            rule.enable();
        } else {
            rule.disable();
        }
    }

    private Long targetFundId(RuleInput input, long ownerId, AlertRuleScope scope) {
        if (scope == AlertRuleScope.GLOBAL) {
            if (input.portfolioFundId() != null) {
                throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "全局规则不能指定基金");
            }
            return null;
        }
        if (input.portfolioFundId() == null || input.portfolioFundId() <= 0) {
            throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "单基金规则必须指定基金");
        }
        boolean tracked = facts.currentFunds(ownerId).stream()
                .anyMatch(fund -> fund.portfolioFundId() == input.portfolioFundId());
        if (!tracked) {
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

    private static AlertRuleType type(RuleInput input) {
        if (input.ruleType() == null || input.ruleType().isBlank()) {
            throw new BusinessException(ErrorCode.ALERT_RULE_TYPE_INVALID, "提醒类型不能为空");
        }
        try {
            return AlertRuleType.valueOf(input.ruleType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.ALERT_RULE_TYPE_INVALID, "不支持的提醒类型");
        }
    }

    public record RuleInput(String scope, Long portfolioFundId, String ruleType, BigDecimal threshold,
                            Boolean enabled) {
    }
}
