package com.fundpilot.backend.alerting.application.gateway.ruleevaluation;

import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 提醒评估所需的外部事实来源：用户关注基金的收益快照与交易日历。 */
public interface AlertFundFactsGateway {

    /** 该用户全部关注基金的当前收益事实。 */
    List<AlertFundFact> currentFunds(long ownerId);

    /** 指定时刻是否为交易日。 */
    boolean isTradingDay(Instant at);

    record AlertFundFact(long portfolioFundId, String fundCode, String fundName,
                         String positionStatus, boolean open, BigDecimal dailyChangePct,
                         BigDecimal holdingAmount, BigDecimal unrealizedPnl,
                         BigDecimal holdingReturnRate, BigDecimal valuationNav,
                         Instant valuationDate, String estimateStatus) {

        /** 该规则类型的观测值口径：涨跌用当日涨跌幅，盈利用持仓收益率；均以小数表示（0.05 即 5%）。 */
        public BigDecimal observedValue(AlertRuleType type) {
            return switch (type) {
                case RISE, DROP -> dailyChangePct;
                case PROFIT -> holdingReturnRate;
            };
        }
    }
}
