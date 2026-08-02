package com.fundpilot.backend.investmentplan.application.gateway.planmanagement;

import java.util.List;
import java.util.Optional;

/** 计划管理对 Portfolio 组合基金有效性与归属的调用方语言。 */
public interface PlanPortfolioFundGateway {
    PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId);
    PortfolioFund requireTracked(long ownerId, long portfolioFundId);
    List<PortfolioFund> findTrackedByOwner(long ownerId);

    /** 定投执行前锁定组合基金并确认仍为 TRACKED。 */
    Optional<PortfolioFund> findTrackedForExecution(long ownerId, long portfolioFundId);
    record PortfolioFund(long id, Long legacyFundId) {}
}
