package com.fundpilot.backend.insights.application.gateway.portfolioreturn;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 组合收益查询所需的跨模块只读事实。 */
public interface ReturnCompositionGateway {
    List<PortfolioFund> findPortfolioFunds(long ownerId);
    List<Position> findPositions(long ownerId);
    List<Position> findPositionsAt(long ownerId, Instant endExclusive);
    List<ReturnFact> findReturnFacts(long ownerId);
    List<ReturnFact> findReturnFactsAt(long ownerId, Instant endExclusive);
    List<Product> findProducts(Set<Long> productIds);
    List<Nav> findLatestTwoNavs(Set<Long> productIds);
    List<Nav> findLatestTwoNavsAt(Set<Long> productIds, Instant endExclusive);
    List<RealtimeValuation> findRealtimeValuations(Set<String> fundCodes);
    List<GroupMembership> findGroupMemberships(long ownerId);
    List<DisciplineClassification> findDisciplineClassifications(long ownerId, Set<Long> portfolioFundIds);

    record PortfolioFund(long id, Long legacyFundId, long fundProductId, boolean tracked,
                         boolean positionWarningEnabled, BigDecimal positionWarningRatio) {}
    record Position(long portfolioFundId, String status, Instant openedAt,
                    BigDecimal costPerShare, BigDecimal confirmedShares) {}
    record ReturnFact(long portfolioFundId, BigDecimal investedAmount, BigDecimal redeemedAmount,
                      BigDecimal externalInvestedAmount, BigDecimal externalRedeemedAmount,
                      BigDecimal feeAmount, BigDecimal realizedPnl, boolean realizedComplete) {}
    record Product(long id, String fundCode, String fundName, String productType, String investmentTarget,
                   String benchmarkIndexCode, String defaultDisciplineCategory) {}
    record Nav(long fundProductId, Instant navDate, BigDecimal unitNav,
               BigDecimal accumulatedNav, Instant firstSeenAt) {}
    record RealtimeValuation(String fundCode, BigDecimal estimatedChangePct, String estimateTime,
                             String baseNavDate, String status) {}
    record GroupMembership(long portfolioFundId, long groupId, String groupName) {}
    record DisciplineClassification(long portfolioFundId, String category) {}
}
