package com.fundpilot.backend.discipline.application.gateway.strategymanagement;
public interface StrategyPortfolioFundGateway {
    PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId);
    PortfolioFund requireTracked(long ownerId, long portfolioFundId);
    record PortfolioFund(long id) {}
}
