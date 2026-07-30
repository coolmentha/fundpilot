package com.fundpilot.backend.insights.infrastructure.gateway.portfolioreturn;

import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.returnfacts.AccountingReturnApi;
import com.fundpilot.backend.discipline.adapter.api.classification.DisciplineClassificationApi;
import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnCompositionGateway;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.marketdata.adapter.api.realtimevaluation.RealtimeValuationApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReturnCompositionGatewayImpl implements ReturnCompositionGateway {
    private final PortfolioFundApi portfolioFunds;
    private final PortfolioGroupingApi groups;
    private final PositionApi positions;
    private final AccountingReturnApi returns;
    private final FundProductApi products;
    private final PublishedNavApi navs;
    private final RealtimeValuationApi valuations;
    private final DisciplineClassificationApi classifications;

    @Override
    public List<PortfolioFund> findPortfolioFunds(long ownerId) {
        return portfolioFunds.findByOwner(ownerId).stream().map(value -> new PortfolioFund(value.id(),
                value.legacyFundId(), value.fundProductId(), value.validity() == PortfolioFundApi.Validity.TRACKED,
                value.positionWarningEnabled(), value.positionWarningRatio()))
                .toList();
    }

    @Override
    public List<Position> findPositions(long ownerId) {
        return positions.findByOwner(ownerId).stream().map(value -> new Position(value.portfolioFundId(),
                value.status().name(), value.openedAt(), value.costPerShare(), value.confirmedShares())).toList();
    }

    @Override
    public List<ReturnFact> findReturnFacts(long ownerId) {
        return returns.findByOwner(ownerId).stream().map(value -> new ReturnFact(value.portfolioFundId(),
                value.investedAmount(), value.redeemedAmount(), value.externalInvestedAmount(),
                value.externalRedeemedAmount(), value.feeAmount(), value.realizedPnl(), value.realizedComplete()))
                .toList();
    }

    @Override
    public List<Product> findProducts(Set<Long> productIds) {
        return products.findByIds(productIds).stream().map(value -> new Product(value.id(), value.fundCode(),
                value.fundName(), value.productType() == null ? null : value.productType().name(),
                value.investmentTarget() == null ? null : value.investmentTarget().name(),
                value.benchmarkIndexCode(), value.defaultDisciplineCategory() == null ? null
                : value.defaultDisciplineCategory().name())).toList();
    }

    @Override
    public List<Nav> findLatestTwoNavs(Set<Long> productIds) {
        return navs.latestTwoByProductIds(productIds).stream().map(value -> new Nav(value.fundProductId(),
                value.navDate(), value.unitNav(), value.accumulatedNav(), value.firstSeenAt())).toList();
    }

    @Override
    public List<RealtimeValuation> findRealtimeValuations(Set<String> fundCodes) {
        return valuations.findByFundCodes(fundCodes).stream().map(value -> new RealtimeValuation(value.fundCode(),
                value.estimatedChangePct(), value.estimateTime(), value.baseNavDate(), value.status())).toList();
    }

    @Override
    public List<GroupMembership> findGroupMemberships(long ownerId) {
        return groups.memberships(ownerId).stream()
                .map(value -> new GroupMembership(value.portfolioFundId(), value.groupId(), value.groupName()))
                .toList();
    }

    @Override
    public List<DisciplineClassification> findDisciplineClassifications(long ownerId, Set<Long> portfolioFundIds) {
        return classifications.findByPortfolioFundIds(ownerId, portfolioFundIds).stream()
                .map(value -> new DisciplineClassification(value.portfolioFundId(), value.category())).toList();
    }
}
