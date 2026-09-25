package com.fundpilot.backend.alerting.application.gateway.notificationdelivery;

import java.math.BigDecimal;
import java.util.List;

/** 提醒邮件投递端口。 */
public interface AlertEmailGateway {

    /** 发送提醒邮件；失败时返回 {@link DeliveryResult#failure(String)}，不向上抛异常。 */
    DeliveryResult send(AlertEmailMessage message);

    /**
     * 一封提醒邮件的全部内容。
     *
     * @param conditionSummary 规则的中文摘要，如「净值与均线偏离率 下穿均线 且 周线 MACD 柱高 柱较上周缩小」
     * @param funds            命中的基金及其逐条条件的现值说明
     */
    record AlertEmailMessage(String recipient, String conditionSummary, int fundCount, List<FundRow> funds) {
        public AlertEmailMessage {
            funds = List.copyOf(funds);
        }

        /**
         * 邮件正文里的一只基金。
         *
         * @param suggestion 建议操作（如「建议卖出 500 份」）；条件型规则为空，仅建议型规则有值
         */
        public record FundRow(long portfolioFundId, String fundCode, String fundName, String conditionDetail,
                              BigDecimal dailyChangePct, BigDecimal valuationNav, BigDecimal holdingAmount,
                              BigDecimal unrealizedPnl, BigDecimal holdingReturnRate, String suggestion) {
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