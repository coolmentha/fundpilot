package com.fundpilot.backend.marketdata.adapter.api.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketIndicatorRefreshApi {
    private final MarketIndicatorRefreshCommandHandler commands;

    public void refreshAll() { commands.refreshAll(); }
    public void refreshBatch(int batchNumber) { commands.refreshBatch(batchNumber); }
    public void refreshBatchAndPublishCompletion(int batchNumber) { commands.refreshBatchAndPublishCompletion(batchNumber); }
    public void refreshOne(long legacyFundId) { commands.refreshOne(legacyFundId); }
    public void refreshOneForPortfolioFund(long portfolioFundId) {
        commands.refreshOneForPortfolioFund(portfolioFundId);
    }
    public void refreshOne(RefreshTarget target) {
        commands.refreshOne(new MarketIndicatorRefreshCommandHandler.RefreshTarget(target.legacyFundId(),
                target.fundProductId(), target.fundCode(), target.fundName(), target.benchmarkIndexCode(),
                target.investmentTarget() == null ? null
                        : TrackedNavProductGateway.InvestmentTarget.valueOf(target.investmentTarget().name())));
    }

    public record RefreshTarget(Long legacyFundId, long fundProductId, String fundCode, String fundName,
                                String benchmarkIndexCode, InvestmentTarget investmentTarget) {}

    public enum InvestmentTarget {
        STOCK, BOND, MIXED, MONEY_MARKET, QDII, FOF, REIT, COMMODITY, ALTERNATIVE
    }
}
