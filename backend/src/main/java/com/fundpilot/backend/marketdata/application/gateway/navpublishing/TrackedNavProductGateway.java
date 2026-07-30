package com.fundpilot.backend.marketdata.application.gateway.navpublishing;

import java.util.List;
import java.util.Optional;

public interface TrackedNavProductGateway {
    List<TrackedProduct> findAll();

    Optional<TrackedProduct> findByLegacyFundId(long legacyFundId);

    Optional<TrackedProduct> findByPortfolioFundId(long portfolioFundId);

    record TrackedProduct(Long legacyFundId, long fundProductId, String fundCode,
                          String fundName, String benchmarkIndexCode,
                          InvestmentTarget investmentTarget) {}

    enum InvestmentTarget {
        STOCK, BOND, MIXED, MONEY_MARKET, QDII, FOF, REIT, COMMODITY, ALTERNATIVE
    }
}
