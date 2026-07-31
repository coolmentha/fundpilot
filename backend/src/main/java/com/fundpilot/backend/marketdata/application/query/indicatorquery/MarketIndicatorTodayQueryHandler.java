package com.fundpilot.backend.marketdata.application.query.indicatorquery;

import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.marketdata.application.query.indicator.MarketIndicatorQueryHandler;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketIndicatorTodayQueryHandler {
    private final OwnedFundProductGateway products;
    private final MarketIndicatorQueryHandler indicators;
    private final Clock clock;

    public Optional<Snapshot> find(long legacyFundId) {
        long productId = products.findOwned(legacyFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "基金不存在: " + legacyFundId))
                .fundProductId();
        return indicators.find(productId, clock.instant()).map(Snapshot::from);
    }

    public Optional<Snapshot> findForPortfolioFund(long portfolioFundId) {
        long productId = products.findOwnedByPortfolioFundId(portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "基金不存在: " + portfolioFundId))
                .fundProductId();
        return indicators.find(productId, clock.instant()).map(Snapshot::from);
    }

    public record Snapshot(String fundCode, Instant snapshotDate, BigDecimal currentNav,
                           Boolean priceAboveYearLine, boolean yearLineRising, String weeklyMacdState,
                           String volumeState, BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {
        static Snapshot from(MarketIndicatorQueryHandler.Result value) {
            return new Snapshot(value.fundCode(), value.snapshotDate(), value.currentNav(),
                    value.priceAboveYearLine(), value.yearLineRising(), value.weeklyMacdState(),
                    value.volumeState(), value.weeklyDropPercent(), value.sixtyDayHigh());
        }
    }
}
