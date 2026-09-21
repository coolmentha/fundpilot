package com.fundpilot.backend.alerting.domain.notification;

import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次提醒尝试的落库记录。
 *
 * <p>同一规则同一交易日最多只允许一条 {@code SENT}（由部分唯一索引兜底并发），失败行不占用额度，
 * 下一次评估会正常重试。
 */
public final class AlertNotification {

    private static final int FAILURE_REASON_MAX_LENGTH = 255;

    private final Long id;
    private final Long version;
    private final long ownerId;
    private final long alertRuleId;
    private final AlertRuleType triggerType;
    private final BigDecimal threshold;
    private final Instant tradingDate;
    private final AlertNotificationStatus status;
    private final String recipientEmail;
    private final int fundCount;
    private final String triggerSummary;
    private final String failureReason;
    private final Instant sentAt;

    private AlertNotification(Long id, Long version, long ownerId, long alertRuleId, AlertRuleType triggerType,
                              BigDecimal threshold, Instant tradingDate, AlertNotificationStatus status,
                              String recipientEmail, int fundCount, String triggerSummary, String failureReason,
                              Instant sentAt) {
        this.id = id;
        this.version = version;
        this.ownerId = positive(ownerId, "用户 ID");
        this.alertRuleId = positive(alertRuleId, "提醒规则 ID");
        this.triggerType = Objects.requireNonNull(triggerType, "提醒类型不能为空");
        this.threshold = Objects.requireNonNull(threshold, "提醒阈值不能为空");
        this.tradingDate = Objects.requireNonNull(tradingDate, "交易日不能为空");
        this.status = Objects.requireNonNull(status, "提醒状态不能为空");
        this.recipientEmail = recipientEmail;
        this.fundCount = fundCount;
        this.triggerSummary = Objects.requireNonNullElse(triggerSummary, "");
        this.failureReason = truncate(failureReason);
        this.sentAt = sentAt;
    }

    public static AlertNotification recordSent(long ownerId, long alertRuleId, AlertRuleType triggerType,
                                               BigDecimal threshold, Instant tradingDate, String recipientEmail,
                                               int fundCount, String triggerSummary, Instant sentAt) {
        return new AlertNotification(null, null, ownerId, alertRuleId, triggerType, threshold, tradingDate,
                AlertNotificationStatus.SENT, recipientEmail, fundCount, triggerSummary, null, sentAt);
    }

    public static AlertNotification recordFailure(long ownerId, long alertRuleId, AlertRuleType triggerType,
                                                  BigDecimal threshold, Instant tradingDate, String recipientEmail,
                                                  int fundCount, String triggerSummary, String failureReason) {
        return new AlertNotification(null, null, ownerId, alertRuleId, triggerType, threshold, tradingDate,
                AlertNotificationStatus.FAILED, recipientEmail, fundCount, triggerSummary, failureReason, null);
    }

    public static AlertNotification rehydrate(long id, Long version, long ownerId, long alertRuleId,
                                              AlertRuleType triggerType, BigDecimal threshold, Instant tradingDate,
                                              AlertNotificationStatus status, String recipientEmail, int fundCount,
                                              String triggerSummary, String failureReason, Instant sentAt) {
        return new AlertNotification(positive(id, "提醒记录 ID"), version, ownerId, alertRuleId, triggerType,
                threshold, tradingDate, status, recipientEmail, fundCount, triggerSummary, failureReason, sentAt);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= FAILURE_REASON_MAX_LENGTH
                ? value
                : value.substring(0, FAILURE_REASON_MAX_LENGTH);
    }

    private static long positive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + "必须为正数");
        return value;
    }

    public Long id() { return id; }
    public Long version() { return version; }
    public long ownerId() { return ownerId; }
    public long alertRuleId() { return alertRuleId; }
    public AlertRuleType triggerType() { return triggerType; }
    public BigDecimal threshold() { return threshold; }
    public Instant tradingDate() { return tradingDate; }
    public AlertNotificationStatus status() { return status; }
    public String recipientEmail() { return recipientEmail; }
    public int fundCount() { return fundCount; }
    public String triggerSummary() { return triggerSummary; }
    public String failureReason() { return failureReason; }
    public Instant sentAt() { return sentAt; }
}
