package com.fundpilot.backend.alerting.application.command.rulemanagement;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionStateRepository;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    private final SuggestionStateRepository states;

    @Transactional
    public AlertRuleQueryHandler.RuleViewResult create(long ownerId, RuleInput input) {
        AlertRuleDraft draft = draft(ownerId, input);
        AlertRule rule = AlertRule.create(ownerId, draft.scope(), draft.portfolioFundId(), draft.kind(),
                draft.conditions(), draft.takeProfit(), input.enabled() == null || input.enabled());
        return views.viewOf(ownerId, rules.save(rule));
    }

    @Transactional
    public AlertRuleQueryHandler.RuleViewResult update(long ownerId, long ruleId, RuleInput input) {
        AlertRule rule = owned(ownerId, ruleId);
        AlertRuleDraft draft = draft(ownerId, input);
        rule.update(draft.scope(), draft.portfolioFundId(), draft.kind(), draft.conditions(), draft.takeProfit());
        setEnabled(rule, input.enabled());
        AlertRule saved = rules.save(rule);
        // 判定配置变了，旧状态不再有效（周期峰值与阶段都按旧配置累积）
        states.deleteByRule(saved.id());
        return views.viewOf(ownerId, saved);
    }

    /** 校验请求并取出结构化草稿；与保存前试算共用同一套校验。 */
    public AlertRuleDraft draft(long ownerId, RuleInput input) {
        return AlertRuleDraft.validate(input, facts.currentFunds(ownerId).stream()
                .map(AlertFundFactsGateway.AlertFundFact::portfolioFundId).toList());
    }

    @Transactional
    public AlertRuleQueryHandler.RuleViewResult changeEnabled(long ownerId, long ruleId, boolean enabled) {
        AlertRule rule = owned(ownerId, ruleId);
        setEnabled(rule, enabled);
        return views.viewOf(ownerId, rules.save(rule));
    }

    @Transactional
    public void delete(long ownerId, long ruleId) {
        long owned = owned(ownerId, ruleId).id();
        states.deleteByRule(owned);
        rules.softDelete(owned);
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

    public record RuleInput(String scope, Long portfolioFundId, String kind, String match,
                            List<ConditionInput> conditions, TakeProfitInput takeProfit, Boolean enabled) {

        /** 条件入参：指标码 + 参数 + 关系 + 阈值（可为空，取指标默认值）。 */
        public record ConditionInput(String indicator, Map<String, Integer> params, String relation,
                                     BigDecimal value) {
        }

        /** 回撤止盈的六个参数（小数表示，如 0.15 即 15%）。 */
        public record TakeProfitInput(BigDecimal activation, BigDecimal pullback, BigDecimal harvest,
                                     BigDecimal minimumHolding, BigDecimal maxSingleSell, Integer cooldownDays) {
        }
    }
}