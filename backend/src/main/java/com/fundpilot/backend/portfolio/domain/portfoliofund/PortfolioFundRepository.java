package com.fundpilot.backend.portfolio.domain.portfoliofund;

import java.util.Optional;
import java.util.List;

public interface PortfolioFundRepository {
    Optional<PortfolioFund> findById(long id);

    /** 悲观锁定组合基金行，串行化跨模块账目确认与基金生命周期变更。 */
    Optional<PortfolioFund> findByIdForUpdate(long id);

    Optional<PortfolioFund> findTrackedByOwnerIdAndFundProductId(long ownerId, long fundProductId);

    Optional<PortfolioFund> findByLegacyFundId(long legacyFundId);

    List<PortfolioFund> findByOwnerId(long ownerId);

    List<PortfolioFund> findAllTracked();

    PortfolioFund save(PortfolioFund portfolioFund);
}
