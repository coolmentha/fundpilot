package com.fundpilot.backend.alerting.application.query.rulemanagement;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.ruletext.AlertRuleText;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提醒规则读模型：规则本体 + 基金标签 + 今日是否已提醒。 */
@Service
@RequiredArgsConstructor
public class AlertRuleQueryHandler {

    private final AlertRuleRepository rules;
    private final AlertNotificationRepository notifications;
    private final AlertFundFactsGateway facts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RuleViewResult> findByOwner(long ownerId) {
        var context = context(ownerId);
        return rules.findByOwnerId(ownerId).stream()
                .map(rule -> view(rule, context))
                .toList();
    }

    /** 单条规则的视图，供写操作回显。 */
    @Transactional(readOnly = true)
    public RuleViewResult viewOf(long ownerId, AlertRule rule) {
        return view(rule, context(ownerId));
    }

    private OwnerContext context(long ownerId) {
        Instant tradingDate = BusinessDay.toDateLabel(clock.instant());
        Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById = facts.currentFunds(ownerId).stream()
                .collect(Collectors.toMap(AlertFundFactsGateway.AlertFundFact::portfolioFundId,
                        Function.identity(), (first, second) -> first));
        Map<Long, AlertNotificationRepository.RuleDailyStatus> statuses =
                notifications.findDailyStatusByOwnerId(ownerId, tradingDate).stream()
                        .collect(Collectors.toMap(AlertNotificationRepository.RuleDailyStatus::alertRuleId,
                                Function.identity(), (first, second) -> first));
        return new OwnerContext(fundsById, statuses);
    }

    private static RuleViewResult view(AlertRule rule, OwnerContext context) {
        var fund = rule.portfolioFundId() == null ? null : context.fundsById().get(rule.portfolioFundId());
        var status = context.statuses().get(rule.id());
        return new RuleViewResult(rule.id(), rule.scope().name(), rule.portfolioFundId(),
                fund == null ? null : fund.fundCode(),
                fund == null ? null : fund.fundName(),
                rule.kind().name(), rule.kind().label(),
                rule.conditions() == null ? null : rule.conditions().match().name(),
                AlertRuleText.summarize(rule),
                conditions(rule), takeProfit(rule), rule.enabled(),
                status != null && status.sentCount() > 0,
                status == null ? null : status.lastSentAt());
    }

    private static List<ConditionView> conditions(AlertRule rule) {
        return rule.conditions() == null ? List.of() : rule.conditions().conditions().stream()
                .map(condition -> new ConditionView(condition.indicator().code(), condition.params(),
                        condition.relation().name(), condition.effectiveThreshold()))
                .toList();
    }

    private static TakeProfitView takeProfit(AlertRule rule) {
        var params = rule.takeProfit();
        return params == null ? null : new TakeProfitView(params.activation(), params.pullback(), params.harvest(),
                params.minimumHolding(), params.maxSingleSell(), params.cooldownDays());
    }

    private record OwnerContext(Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById,
                                Map<Long, AlertNotificationRepository.RuleDailyStatus> statuses) {
    }

    public record RuleViewResult(long id, String scope, Long portfolioFundId, String fundCode, String fundName,
                                 String kind, String kindLabel, String match, String conditionSummary,
                                 List<ConditionView> conditions, TakeProfitView takeProfit, boolean enabled,
                                 boolean todaySent, Instant lastTriggeredAt) {
    }

    /** 单条条件视图：阈值仅在与阈值比较的关系下存在。 */
    public record ConditionView(String indicator, Map<String, Integer> params, String relation, BigDecimal value) {
    }

    /** 回撤止盈的六个参数视图；非回撤止盈规则为空。 */
    public record TakeProfitView(BigDecimal activation, BigDecimal pullback, BigDecimal harvest,
                                 BigDecimal minimumHolding, BigDecimal maxSingleSell, int cooldownDays) {
    }
}