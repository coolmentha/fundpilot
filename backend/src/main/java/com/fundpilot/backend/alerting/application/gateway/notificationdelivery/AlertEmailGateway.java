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
     * @param skipped          因数据不可用而无法判定的基金及原因；空列表表示没有跳过任何基金
     */
    record AlertEmailMessage(String recipient, String conditionSummary, int fundCount, List<FundRow> funds,
                             List<SkippedFund> skipped) {

        /** 无跳过基金时的常用形态。 */
        public AlertEmailMessage(String recipient, String conditionSummary, int fundCount, List<FundRow> funds) {
            this(recipient, conditionSummary, fundCount, funds, List.of());
        }

        public AlertEmailMessage {
            funds = List.copyOf(funds);
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
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

        /**
         * 因数据不可用而没能参与判定的基金。
         *
         * <p>列出来是为了让「这次只提醒了 3 只」可被解释：用户能分清是没越线，还是该基金缺当日估值。
         */
        public record SkippedFund(String fundCode, String fundName, String reason) {
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