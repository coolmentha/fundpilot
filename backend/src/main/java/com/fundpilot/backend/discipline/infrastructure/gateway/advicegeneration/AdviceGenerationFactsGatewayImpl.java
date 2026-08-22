package com.fundpilot.backend.discipline.infrastructure.gateway.advicegeneration;

import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.discipline.application.gateway.advicegeneration.AdviceGenerationFactsGateway;
import com.fundpilot.backend.discipline.domain.advice.AdvicePolicy;
import com.fundpilot.backend.marketdata.adapter.api.indicator.MarketIndicatorApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AdviceGenerationFactsGatewayImpl implements AdviceGenerationFactsGateway {
    private static final int MIN_HOLD_TRADING_DAYS = 5;

    private final PortfolioFundApi portfolioFunds;
    private final FundProductApi products;
    private final PositionApi positions;
    private final MarketIndicatorApi indicators;
    private final PublishedNavApi navs;
    private final TransactionApi transactions;
    private final TradingCalendarApi calendar;

    @Override
    public boolean isTradingDay(Instant businessDate) {
        return calendar.isTradingDay(businessDate);
    }

    @Override
    public long tradingDaysBetween(Instant fromExclusive, Instant toInclusive) {
        return calendar.countBetween(fromExclusive, toInclusive);
    }

    @Override
    public Optional<Facts> load(long ownerId, long portfolioFundId, Instant businessDate) {
        var portfolioFund = portfolioFunds.findOwned(ownerId, portfolioFundId)
                .filter(value -> value.validity() == PortfolioFundApi.Validity.TRACKED);
        if (portfolioFund.isEmpty()) {
            return Optional.empty();
        }
        var position = positions.findOwned(ownerId, portfolioFundId);
        var product = products.findById(portfolioFund.get().fundProductId());
        if (position.isEmpty() || product.isEmpty()) {
            return Optional.empty();
        }
        long productId = portfolioFund.get().fundProductId();
        var latestNav = navs.latest(productId);
        var market = indicators.find(productId, businessDate).map(value -> new MarketSnapshot(
                value.currentNav(), value.priceAboveYearLine(), value.yearLineRising(),
                enumValue(AdvicePolicy.MacdState.class, value.weeklyMacdState()),
                enumValue(AdvicePolicy.VolumeState.class, value.volumeState()),
                value.weeklyDropPercent(), value.sixtyDayHigh()))
                .orElse(null);
        var ledger = transactions.findByPortfolioFund(ownerId, portfolioFundId);
        Instant lastBuy = ledger.stream()
                .filter(value -> value.status() == TransactionApi.Status.CONFIRMED)
                .filter(value -> value.source() == TransactionApi.Source.INCREASE
                        || value.source() == TransactionApi.Source.TRANSFER_IN
                        || value.source() == TransactionApi.Source.INVEST)
                .map(value -> value.tradeDate() != null ? value.tradeDate() : value.confirmTime())
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        BigDecimal holdingShares = position.get().confirmedShares() == null
                ? BigDecimal.ZERO : position.get().confirmedShares();
        BigDecimal trackedShares = BigDecimal.ZERO;
        BigDecimal matureShares = BigDecimal.ZERO;
        for (PositionApi.OpenLot lot : positions.openLots(ownerId, portfolioFundId)) {
            trackedShares = trackedShares.add(lot.remainingShares());
            if (calendar.countBetween(lot.acquireDate(), businessDate) >= MIN_HOLD_TRADING_DAYS) {
                matureShares = matureShares.add(lot.remainingShares());
            }
        }
        matureShares = matureShares.add(holdingShares.subtract(trackedShares).max(BigDecimal.ZERO))
                .min(holdingShares);
        return Optional.of(new Facts(portfolioFundId, ownerId, productId,
                product.get().productType() == null ? null
                        : AdvicePolicy.ProductType.valueOf(product.get().productType().name()),
                AdvicePolicy.PositionStatus.valueOf(position.get().status().name()),
                position.get().openedAt(), position.get().costPerShare(),
                holdingShares, market, latestNav.map(PublishedNavApi.PublishedNav::unitNav).orElse(null),
                latestNav.map(PublishedNavApi.PublishedNav::accumulatedNav).orElse(null),
                navs.peakAccumulatedNav(productId, null).orElse(null),
                navs.peakAccumulatedNav(productId, position.get().openedAt()).orElse(null),
                lastBuy, matureShares));
    }

    /** 未知枚举值(如 marketdata 新增状态)降级为 null,由策略按"条件不成立"处理。 */
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
