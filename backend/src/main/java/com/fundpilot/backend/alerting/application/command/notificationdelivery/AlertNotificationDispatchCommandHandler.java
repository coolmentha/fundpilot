package com.fundpilot.backend.alerting.application.command.notificationdelivery;

import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.notification.AlertNotification;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单条命中规则的发送与落库：组装邮件消息、投递、记录 {@code SENT}/{@code FAILED}。 */
@Service
@RequiredArgsConstructor
public class AlertNotificationDispatchCommandHandler {

    private static final String MISSING_RECIPIENT = "未配置提醒邮箱";

    private final AlertNotificationRepository notifications;
    private final AlertEmailGateway deliveries;
    private final Clock clock;

    /**
     * 投递一条已命中的规则提醒，并把结果落库。
     *
     * <p>独立事务：发送失败或唯一索引冲突只回滚本条记录，不影响其余规则的评估。
     */
    @Transactional
    public DispatchResult dispatch(long ownerId, AlertRule rule, Instant tradingDate, String recipient,
                                   List<AlertFundFactsGateway.AlertFundFact> funds, String triggerSummary) {
        if (recipient == null || recipient.isBlank()) {
            recordFailure(ownerId, rule, tradingDate, null, funds.size(), triggerSummary, MISSING_RECIPIENT);
            return new DispatchResult(false, MISSING_RECIPIENT);
        }
        AlertEmailGateway.DeliveryResult result = deliveries.send(new AlertEmailGateway.AlertEmailMessage(
                recipient, rule.type(), rule.threshold(), funds.size(), rows(rule, funds)));
        if (result.sent()) {
            notifications.save(AlertNotification.recordSent(ownerId, rule.id(), rule.type(), rule.threshold(),
                    tradingDate, recipient, funds.size(), triggerSummary, clock.instant()));
        } else {
            recordFailure(ownerId, rule, tradingDate, recipient, funds.size(), triggerSummary,
                    result.failureReason());
        }
        return new DispatchResult(result.sent(), result.failureReason());
    }

    private void recordFailure(long ownerId, AlertRule rule, Instant tradingDate, String recipient, int fundCount,
                               String triggerSummary, String failureReason) {
        notifications.save(AlertNotification.recordFailure(ownerId, rule.id(), rule.type(), rule.threshold(),
                tradingDate, recipient, fundCount, triggerSummary, failureReason));
    }

    private static List<AlertEmailGateway.AlertEmailMessage.FundRow> rows(
            AlertRule rule, List<AlertFundFactsGateway.AlertFundFact> funds) {
        return funds.stream()
                .map(fund -> new AlertEmailGateway.AlertEmailMessage.FundRow(fund.portfolioFundId(),
                        fund.fundCode(), fund.fundName(), fund.observedValue(rule.type()), fund.dailyChangePct(),
                        fund.valuationNav(), fund.holdingAmount(), fund.unrealizedPnl(),
                        fund.holdingReturnRate()))
                .toList();
    }

    public record DispatchResult(boolean sent, String failureReason) {
    }
}
