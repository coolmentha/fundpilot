package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import com.fundpilot.backend.accounting.adapter.api.fundonboarding.PortfolioFundOnboardingApi;
import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.marketdata.adapter.api.indicatorrefresh.MarketIndicatorRefreshApi;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ImportedHoldingGatewayImpl implements ImportedHoldingGateway {
    private static final BigDecimal DEFAULT_WARNING_RATIO = new BigDecimal("0.30");
    private final FundProductApi products;
    private final MarketIndicatorRefreshApi marketDataRefresh;
    private final PortfolioFundApi portfolioFunds;
    private final PortfolioGroupingApi groups;
    private final PortfolioFundOnboardingApi onboarding;
    private final PositionApi positions;
    private final TransactionApi transactions;

    @Override
    public Optional<LocalHolding> find(long ownerId, String fundCode) {
        return products.findByCode(fundCode).flatMap(product -> portfolioFunds.findByOwner(ownerId).stream()
                .filter(fund -> fund.fundProductId() == product.id()
                        && fund.validity() == PortfolioFundApi.Validity.TRACKED)
                .findFirst().map(fund -> new LocalHolding(fund.id(), fund.legacyFundId(),
                        positions.findOwned(ownerId, fund.id()).map(PositionApi.Position::confirmedShares)
                                .orElse(BigDecimal.ZERO))));
    }

    @Override
    public ImportedHolding create(long ownerId, String fundCode, String fundName, BigDecimal shares,
                                  BigDecimal costPerShare, List<String> groupNames) {
        var product = products.ensure(new FundProductApi.EnsureProduct(fundCode, fundName, fundName, null));
        marketDataRefresh.refreshOne(new MarketIndicatorRefreshApi.RefreshTarget(null, product.id(), fundCode,
                fundName, null, null));
        var result = onboarding.onboard(new PortfolioFundOnboardingApi.OnboardPortfolioFund(null, ownerId,
                product.id(), true, DEFAULT_WARNING_RATIO, shares, costPerShare, null));
        groups.assignByNames(new PortfolioGroupingApi.AssignByNames(ownerId, result.portfolioFundId(), groupNames));
        return portfolioFunds.findOwned(ownerId, result.portfolioFundId())
                .map(fund -> new ImportedHolding(fund.id(), fund.legacyFundId()))
                .orElseThrow();
    }

    @Override
    public boolean synchronize(long ownerId, long portfolioFundId, BigDecimal targetShares) {
        return transactions.adjustToHoldingShares(new TransactionApi.AdjustToHoldingShares(
                ownerId, portfolioFundId, targetShares)).transaction() != null;
    }
}
