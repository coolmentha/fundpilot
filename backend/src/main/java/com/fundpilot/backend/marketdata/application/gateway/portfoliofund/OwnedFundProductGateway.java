package com.fundpilot.backend.marketdata.application.gateway.portfoliofund;

import java.util.Optional;

public interface OwnedFundProductGateway {
    Optional<Product> findOwned(long legacyFundId);

    Optional<Product> findOwnedByPortfolioFundId(long portfolioFundId);

    record Product(long fundProductId, String fundCode, String benchmarkIndexCode, ProductType productType) {}
    enum ProductType { ETF, INDEX, INDEX_ENHANCED, ACTIVE }
}
