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
     *
     * <p>网格两项（{@code baselineNav} 与其成交日 {@code baselineNavDate}）来自最近一条有成交净值的
     * 已确认流水，是该规则唯一的基准线来源；没有可用的历史成交时为 null。
     */
    record AlertFundFact(long portfolioFundId, long fundProductId, String fundCode, String fundName,
                         String positionStatus, boolean open, String productType, Instant openedAt,
                         BigDecimal costPerShare, BigDecimal holdingShares, BigDecimal matureRedeemableShares,
                         BigDecimal dailyChangePct, BigDecimal holdingAmount, BigDecimal unrealizedPnl,
                         BigDecimal holdingReturnRate, BigDecimal valuationNav, BigDecimal currentUnitNav,
                         BigDecimal currentAccumulatedNav, Instant lastBuyTime, Instant valuationDate,
                         String estimateStatus, BigDecimal baselineNav, Instant baselineNavDate) {

        /** 是否当前在持：建议型规则只对在持基金给出建议。 */
        public boolean held() {
            return "OPEN".equals(positionStatus);
        }

        /** 当日盘中估值是否可用于网格判定：只有明确取到当日估值才算可用。 */
        public boolean estimateAvailable() {
            return "AVAILABLE".equals(estimateStatus) && valuationNav != null && valuationNav.signum() > 0;
        }
    }
}