package com.fundpilot.backend.investmentplan.application.gateway.planmanagement;

import java.util.List;

/** 计划管理对 Portfolio 组合基金有效性与归属的调用方语言。 */
public interface PlanPortfolioFundGateway {
    PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId);
    PortfolioFund requireTracked(long ownerId, long portfolioFundId);
    List<PortfolioFund> findTrackedByOwner(long ownerId);
    record PortfolioFund(long id, Long legacyFundId) {}
}
