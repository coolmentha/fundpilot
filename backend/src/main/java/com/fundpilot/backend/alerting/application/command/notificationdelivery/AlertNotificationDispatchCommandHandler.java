package com.fundpilot.backend.alerting.application.command.notificationdelivery;

import com.fundpilot.backend.alerting.application.condition.AlertConditionJsonCodec;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway;
import com.fundpilot.backend.alerting.application.ruletext.AlertRuleText;
import com.fundpilot.backend.alerting.application.suggestion.TakeProfitParamsJsonCodec;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.notification.AlertNotification;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单条命中规则的发送与落库：组装邮件消息、投递、记录 {@code SENT}/{@code FAILED}。 */
@Service
@RequiredArgsConstructor
public class AlertNotificationDispatchCommandHandler {

    private static final String MISSING_RECIPIENT = "未配置提醒邮箱";
    private static final int SUMMARY_MAX_LENGTH = 500;

    private final AlertNotificationRepository notifications;
    private final AlertEmailGateway deliveries;
    private final Clock clock;

    /**
     * 投递一条已命中的规则提醒，并把结果落库。
     *
     * <p>独立事务：发送失败或唯一索引冲突只回滚本条记录，不影响其余规则的评估。
     *
     * @param rows 命中的基金及其逐条条件的现值说明，由评估侧算好以避免重复求值
     */
    @Transactional
    public DispatchResult dispatch(long ownerId, AlertRule rule, Instant tradingDate, String recipient,
                                   List<AlertEmailGateway.AlertEmailMessage.FundRow> rows) {
        String snapshot = snapshot(rule);
        String triggerSummary = summarize(rows);
        if (recipient == null || recipient.isBlank()) {
            recordFailure(ownerId, rule.id(), snapshot, tradingDate, null, rows.size(), triggerSummary,
                    MISSING_RECIPIENT);
            return new DispatchResult(false, MISSING_RECIPIENT);
        }
        AlertEmailGateway.DeliveryResult result = deliveries.send(new AlertEmailGateway.AlertEmailMessage(
                recipient, AlertRuleText.summarize(rule), rows.size(), rows));
        if (result.sent()) {
            notifications.save(AlertNotification.recordSent(ownerId, rule.id(), snapshot, tradingDate, recipient,
                    rows.size(), triggerSummary, clock.instant()));
        } else {
            recordFailure(ownerId, rule.id(), snapshot, tradingDate, recipient, rows.size(), triggerSummary,
                    result.failureReason());
        }
        return new DispatchResult(result.sent(), result.failureReason());
    }

    /** 条件快照：条件型规则存条件数组，回撤止盈存六个参数。 */
    private static String snapshot(AlertRule rule) {
        return rule.conditions() != null
                ? AlertConditionJsonCodec.write(rule.conditions())
                : TakeProfitParamsJsonCodec.write(rule.takeProfit());
    }

    private void recordFailure(long ownerId, long ruleId, String snapshot, Instant tradingDate, String recipient,
                               int fundCount, String triggerSummary, String failureReason) {
        notifications.save(AlertNotification.recordFailure(ownerId, ruleId, snapshot, tradingDate, recipient,
                fundCount, triggerSummary, failureReason));
    }

    /** 逐只基金列出命中的条件与现值、建议操作，超出列长度即截断。 */
    private static String summarize(List<AlertEmailGateway.AlertEmailMessage.FundRow> rows) {
        String text = rows.stream()
                .map(row -> row.fundName() + "(" + row.fundCode() + ") 命中：" + row.conditionDetail()
                        + (row.suggestion() == null ? "" : "；" + row.suggestion()))
                .collect(Collectors.joining("; "));
        return text.length() <= SUMMARY_MAX_LENGTH ? text : text.substring(0, SUMMARY_MAX_LENGTH);
    }

    public record DispatchResult(boolean sent, String failureReason) {
    }
}