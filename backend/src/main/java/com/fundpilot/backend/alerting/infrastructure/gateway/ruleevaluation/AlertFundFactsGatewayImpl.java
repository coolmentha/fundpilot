package com.fundpilot.backend.alerting.infrastructure.gateway.ruleevaluation;

import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.insights.adapter.api.fundreturn.FundReturnApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组合 insights 的收益快照、accounting 的持仓事实、marketdata 的净值与交易日历、productcatalog 的产品类型。
 *
 * <p>建议型规则需要「单位成本、确认份额、成熟可赎回份额、累计净值」四项，其中成熟可赎回份额必须经
 * accounting 的持仓批次才能算出，因此这里直接取持仓事实而不是扩展 insights 的收益快照。
 */
@Component
@RequiredArgsConstructor
public class AlertFundFactsGatewayImpl implements AlertFundFactsGateway {

    private static final int MIN_HOLD_TRADING_DAYS = 5;

    private final FundReturnApi fundReturns;
    private final TradingCalendarApi tradingCalendar;
    private final PositionApi positions;
    private final PublishedNavApi navs;
    private final FundProductApi products;
    private final TransactionApi transactions;
    private final Clock clock;

    @Override
    public List<AlertFundFact> currentFunds(long ownerId) {
        Instant businessDate = BusinessDay.toDateLabel(clock.instant());
        return fundReturns.currentFunds(ownerId).stream()
                .map(snapshot -> fact(ownerId, businessDate, snapshot))
                .toList();
    }

    @Override
    public boolean isTradingDay(Instant at) {
        return tradingCalendar.isTradingDay(at);
    }

    @Override
    public long tradingDaysBetween(Instant fromExclusive, Instant toInclusive) {
        return tradingCalendar.countBetween(fromExclusive, toInclusive);
    }

    private AlertFundFact fact(long ownerId, Instant businessDate, FundReturnApi.FundReturnSnapshot snapshot) {
        long portfolioFundId = snapshot.portfolioFundId();
        PositionApi.Position position = positions.findOwned(ownerId, portfolioFundId).orElse(null);
        BigDecimal holdingShares = position == null ? null : position.confirmedShares();
        PublishedNavApi.PublishedNav nav = navs.latest(snapshot.fundProductId()).orElse(null);
        return new AlertFundFact(portfolioFundId, snapshot.fundProductId(), snapshot.fundCode(),
                snapshot.fundName(), snapshot.positionStatus(), snapshot.open(), productType(snapshot),
                position == null ? null : position.openedAt(), position == null ? null : position.costPerShare(),
                holdingShares, matureRedeemableShares(ownerId, portfolioFundId, businessDate, holdingShares),
                snapshot.dailyChangePct(), snapshot.holdingAmount(), snapshot.unrealizedPnl(),
                snapshot.holdingReturnRate(), snapshot.valuationNav(),
                nav == null ? null : nav.unitNav(), nav == null ? null : nav.accumulatedNav(),
                lastBuyTime(ownerId, portfolioFundId), snapshot.valuationDate(), snapshot.estimateStatus());
    }

    private String productType(FundReturnApi.FundReturnSnapshot snapshot) {
        return products.findById(snapshot.fundProductId())
                .map(product -> product.productType() == null ? null : product.productType().name())
                .orElse(null);
    }

    /** 成熟可赎回份额：持仓批次中已满 5 个交易日的份额，加上未被批次覆盖的部分（照搬旧口径）。 */
    private BigDecimal matureRedeemableShares(long ownerId, long portfolioFundId, Instant businessDate,
                                              BigDecimal holdingShares) {
        if (holdingShares == null) {
            return null;
        }
        BigDecimal trackedShares = BigDecimal.ZERO;
        BigDecimal matureShares = BigDecimal.ZERO;
        for (PositionApi.OpenLot lot : positions.openLots(ownerId, portfolioFundId)) {
            BigDecimal remaining = lot.remainingShares() == null ? BigDecimal.ZERO : lot.remainingShares();
            trackedShares = trackedShares.add(remaining);
            if (tradingCalendar.countBetween(lot.acquireDate(), businessDate) >= MIN_HOLD_TRADING_DAYS) {
                matureShares = matureShares.add(remaining);
            }
        }
        return matureShares.add(holdingShares.subtract(trackedShares).max(BigDecimal.ZERO)).min(holdingShares);
    }

    /** 最近一次加仓时间：已确认的加仓/转入/申购里最晚的交易日。 */
    private Instant lastBuyTime(long ownerId, long portfolioFundId) {
        return transactions.findByPortfolioFund(ownerId, portfolioFundId).stream()
                .filter(value -> value.status() == TransactionApi.Status.CONFIRMED)
                .filter(value -> value.source() == TransactionApi.Source.INCREASE
                        || value.source() == TransactionApi.Source.TRANSFER_IN
                        || value.source() == TransactionApi.Source.INVEST)
                .map(value -> value.tradeDate() != null ? value.tradeDate() : value.confirmTime())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(ChinaTradingDate::toUtcDate)
                .orElse(null);
    }
}