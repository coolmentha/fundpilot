package com.fundpilot.backend.portfolio.domain.portfoliofund;

import java.util.Optional;
import java.util.List;

public interface PortfolioFundRepository {
    Optional<PortfolioFund> findById(long id);

    Optional<PortfolioFund> findTrackedByOwnerIdAndFundProductId(long ownerId, long fundProductId);

    Optional<PortfolioFund> findByLegacyFundId(long legacyFundId);

    List<PortfolioFund> findByOwnerId(long ownerId);

    PortfolioFund save(PortfolioFund portfolioFund);
}
