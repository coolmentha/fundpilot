package com.fundpilot.backend.accounting.domain.position;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 持仓聚合的持久化需求。 */
public interface PositionRepository {

    Position save(Position position);

    Optional<Position> findByPortfolioFund(long portfolioFundId);

    List<Position> findByPortfolioFundIds(Collection<Long> portfolioFundIds);

    List<Position> findByOwner(long ownerId);
}
