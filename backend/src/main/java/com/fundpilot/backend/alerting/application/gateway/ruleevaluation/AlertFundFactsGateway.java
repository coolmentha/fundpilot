package com.fundpilot.backend.alerting.application.gateway.ruleevaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 提醒评估所需的外部事实来源：用户关注基金的收益快照、持仓事实与交易日历。 */
public interface AlertFundFactsGateway {

    /** 该用户全部关注基金的当前收益与持仓事实。 */
    List<AlertFundFact> currentFunds(long ownerId);

    /** 指定时刻是否为交易日。 */
    boolean isTradingDay(Instant at);

    /** 两个日期之间（不含起点、含终点）的交易日数，用于冷静期与最短持有天数。 */
    long tradingDaysBetween(Instant fromExclusive, Instant toInclusive);

    /**
     * 单只基金的事实，也是「基金事实类」指标（涨跌幅、持仓收益率）与建议型规则判定的取值来源。
     *
     * <p>持仓四项（单位成本、确认份额、成熟可赎回份额、累计净值）只在建议型规则里使用；未持仓基金为
     * 空值语义，由规则按「无数据」处理。
     */
    record AlertFundFact(long portfolioFundId, long fundProductId, String fundCode, String fundName,
                         String positionStatus, boolean open, String productType, Instant openedAt,
                         BigDecimal costPerShare, BigDecimal holdingShares, BigDecimal matureRedeemableShares,
                         BigDecimal dailyChangePct, BigDecimal holdingAmount, BigDecimal unrealizedPnl,
                         BigDecimal holdingReturnRate, BigDecimal valuationNav, BigDecimal currentUnitNav,
                         BigDecimal currentAccumulatedNav, Instant lastBuyTime, Instant valuationDate,
                         String estimateStatus) {

        /** 是否当前在持：建议型规则只对在持基金给出建议。 */
        public boolean held() {
            return "OPEN".equals(positionStatus);
        }
    }
}