package com.fundpilot.backend.alerting.application.gateway.notificationdelivery;

import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import java.math.BigDecimal;
import java.util.List;

/** 提醒邮件投递端口。 */
public interface AlertEmailGateway {

    /** 发送提醒邮件；失败时返回 {@link DeliveryResult#failure(String)}，不向上抛异常。 */
    DeliveryResult send(AlertEmailMessage message);

    record AlertEmailMessage(String recipient, AlertRuleType ruleType, BigDecimal threshold,
                             int fundCount, List<FundRow> funds) {
        public AlertEmailMessage {
            funds = List.copyOf(funds);
        }

        public record FundRow(long portfolioFundId, String fundCode, String fundName,
                       BigDecimal observedValue, BigDecimal dailyChangePct,
                       BigDecimal valuationNav, BigDecimal holdingAmount,
                       BigDecimal unrealizedPnl, BigDecimal holdingReturnRate) {
        }
    }

    record DeliveryResult(boolean sent, String failureReason) {

        public static DeliveryResult success() {
            return new DeliveryResult(true, null);
        }

        public static DeliveryResult failure(String reason) {
            return new DeliveryResult(false, reason);
        }
    }
}
