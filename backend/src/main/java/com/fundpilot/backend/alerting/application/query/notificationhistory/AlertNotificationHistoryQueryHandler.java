package com.fundpilot.backend.alerting.application.query.notificationhistory;

import com.fundpilot.backend.alerting.domain.notification.AlertNotification;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提醒发送历史读模型。 */
@Service
@RequiredArgsConstructor
public class AlertNotificationHistoryQueryHandler {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final AlertNotificationRepository notifications;

    @Transactional(readOnly = true)
    public List<NotificationViewResult> findLatest(long ownerId, Integer limit) {
        int effective = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return notifications.findLatestByOwnerId(ownerId, effective).stream()
                .map(NotificationViewResult::from)
                .toList();
    }

    public record NotificationViewResult(long id, long alertRuleId, String ruleType, BigDecimal threshold,
                                         String conditionsSnapshot, String triggerSummary, int fundCount,
                                         String status, String failureReason, Instant tradingDate, Instant sentAt,
                                         String recipientEmail) {

        static NotificationViewResult from(AlertNotification notification) {
            return new NotificationViewResult(notification.id(), notification.alertRuleId(),
                    notification.triggerType() == null ? null : notification.triggerType().name(),
                    notification.threshold(), notification.conditionsSnapshot(), notification.triggerSummary(),
                    notification.fundCount(), notification.status().name(), notification.failureReason(),
                    notification.tradingDate(), notification.sentAt(), notification.recipientEmail());
        }
    }
}
