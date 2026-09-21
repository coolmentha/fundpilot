package com.fundpilot.backend.alerting.application.query.ruleevaluation;

import com.fundpilot.backend.alerting.application.command.notificationdelivery.AlertNotificationDispatchCommandHandler;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertRecipientGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 每日提醒评估的唯一编排入口。
 *
 * <p>不声明事务：每条规则的发送与落库由 {@link AlertNotificationDispatchCommandHandler} 独立事务完成，
 * 单条规则的异常或唯一索引冲突不会污染其它规则。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleEvaluationQueryHandler {

    private static final int SUMMARY_MAX_LENGTH = 500;

    private final AlertRuleRepository rules;
    private final AlertNotificationRepository notifications;
    private final AlertFundFactsGateway facts;
    private final AlertRecipientGateway recipients;
    private final AlertNotificationDispatchCommandHandler dispatches;
    private final Clock clock;

    /** 评估全部启用规则并按用户发送提醒，返回计数供任务日志与测试断言。 */
    public EvaluationResult evaluate() {
        Instant now = clock.instant();
        if (!facts.isTradingDay(now)) {
            return new EvaluationResult(0, 0, 0, 0);
        }
        Instant tradingDate = BusinessDay.toDateLabel(now);
        Map<Long, List<AlertRule>> rulesByOwner = rules.findAllEnabled().stream()
                .collect(Collectors.groupingBy(AlertRule::ownerId, LinkedHashMap::new, Collectors.toList()));

        int evaluated = 0;
        int matched = 0;
        int sent = 0;
        int failed = 0;
        for (Map.Entry<Long, List<AlertRule>> entry : rulesByOwner.entrySet()) {
            long ownerId = entry.getKey();
            List<AlertRule> ownerRules = entry.getValue();
            Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById = facts.currentFunds(ownerId).stream()
                    .collect(Collectors.toMap(AlertFundFactsGateway.AlertFundFact::portfolioFundId,
                            Function.identity(), (first, second) -> first, LinkedHashMap::new));
            Set<Coverage> covered = ownerRules.stream()
                    .filter(rule -> rule.portfolioFundId() != null)
                    .map(rule -> new Coverage(rule.portfolioFundId(), rule.type()))
                    .collect(Collectors.toSet());
            String recipient = recipients.emailOf(ownerId).orElse(null);

            for (AlertRule rule : ownerRules) {
                evaluated++;
                try {
                    List<AlertFundFactsGateway.AlertFundFact> hits = hitFunds(rule, fundsById, covered);
                    if (hits.isEmpty()) {
                        continue;
                    }
                    if (notifications.countSentByRuleAndTradingDate(rule.id(), tradingDate) >= 1) {
                        continue;
                    }
                    matched++;
                    var result = dispatches.dispatch(ownerId, rule, tradingDate, recipient, hits,
                            summarize(rule.type(), hits));
                    if (result.sent()) {
                        sent++;
                    } else {
                        failed++;
                    }
                } catch (DataIntegrityViolationException exception) {
                    log.info("提醒规则 {} 当日已由其它实例发送，跳过", rule.id());
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("提醒规则 {} 评估失败", rule.id(), exception);
                }
            }
        }
        EvaluationResult result = new EvaluationResult(evaluated, matched, sent, failed);
        if (evaluated > 0) {
            log.info("价格提醒评估完成: {}", result);
        }
        return result;
    }

    /** 该规则的目标基金中命中阈值的部分；单基金规则只匹配自身，全局规则剔除已被单基金规则覆盖的基金。 */
    private static List<AlertFundFactsGateway.AlertFundFact> hitFunds(
            AlertRule rule, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById, Set<Coverage> covered) {
        return targets(rule, fundsById, covered).stream()
                .filter(fund -> rule.appliesTo(fund.open()))
                .filter(fund -> rule.triggered(fund.observedValue(rule.type())))
                .toList();
    }

    private static List<AlertFundFactsGateway.AlertFundFact> targets(
            AlertRule rule, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById, Set<Coverage> covered) {
        if (!rule.global()) {
            AlertFundFactsGateway.AlertFundFact fund = fundsById.get(rule.portfolioFundId());
            return fund == null ? List.of() : List.of(fund);
        }
        return fundsById.values().stream()
                .filter(fund -> !covered.contains(new Coverage(fund.portfolioFundId(), rule.type())))
                .toList();
    }

    private static String summarize(AlertRuleType type, List<AlertFundFactsGateway.AlertFundFact> funds) {
        String text = funds.stream()
                .map(fund -> fund.fundName() + "(" + fund.fundCode() + ") " + phrase(type)
                        + percent(fund.observedValue(type)) + "%")
                .collect(Collectors.joining("; "));
        return text.length() <= SUMMARY_MAX_LENGTH ? text : text.substring(0, SUMMARY_MAX_LENGTH);
    }

    private static String phrase(AlertRuleType type) {
        return switch (type) {
            case RISE -> "上涨";
            case DROP -> "下跌";
            case PROFIT -> "盈利";
        };
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 已被单基金规则覆盖的「基金 + 提醒类型」组合。 */
    private record Coverage(long portfolioFundId, AlertRuleType type) {
    }

    public record EvaluationResult(int evaluatedRules, int matchedRules, int sentRules, int failedRules) {
    }
}
