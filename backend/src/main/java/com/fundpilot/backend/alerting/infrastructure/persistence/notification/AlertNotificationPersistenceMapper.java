package com.fundpilot.backend.alerting.infrastructure.persistence.notification;

import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import com.fundpilot.backend.alerting.domain.notification.AlertNotification;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationStatus;

final class AlertNotificationPersistenceMapper {

    private AlertNotificationPersistenceMapper() {
    }

    static AlertNotification toDomain(AlertNotificationJpaEntity entity) {
        return AlertNotification.rehydrate(entity.getId(), entity.getVersion(), entity.getOwnerId(),
                entity.getAlertRuleId(), type(entity.getTriggerType()), entity.getThreshold(),
                entity.getConditionsSnapshot(), entity.getTradingDate(),
                AlertNotificationStatus.valueOf(entity.getStatus()), entity.getRecipientEmail(),
                entity.getFundCount(), entity.getTriggerSummary(), entity.getFailureReason(), entity.getSentAt());
    }

    static AlertNotificationJpaEntity toEntity(AlertNotification notification) {
        AlertNotificationJpaEntity entity = new AlertNotificationJpaEntity();
        entity.setOwnerId(notification.ownerId());
        entity.setAlertRuleId(notification.alertRuleId());
        entity.setTriggerType(notification.triggerType() == null ? null : notification.triggerType().name());
        entity.setThreshold(notification.threshold());
        entity.setConditionsSnapshot(notification.conditionsSnapshot());
        entity.setTradingDate(notification.tradingDate());
        entity.setStatus(notification.status().name());
        entity.setRecipientEmail(notification.recipientEmail());
        entity.setFundCount(notification.fundCount());
        entity.setTriggerSummary(notification.triggerSummary());
        entity.setFailureReason(notification.failureReason());
        entity.setSentAt(notification.sentAt());
        return entity;
    }

    /** 存量兼容：仅固定三阈值模型时期的历史行有值。 */
    private static AlertRuleType type(String value) {
        return value == null || value.isBlank() ? null : AlertRuleType.valueOf(value);
    }
}