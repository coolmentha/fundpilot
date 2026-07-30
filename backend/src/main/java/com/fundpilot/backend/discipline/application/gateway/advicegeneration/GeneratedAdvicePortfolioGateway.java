package com.fundpilot.backend.discipline.application.gateway.advicegeneration;

/** 将 legacy 基金标识解析为 Discipline 所属的组合基金。 */
public interface GeneratedAdvicePortfolioGateway {
    PortfolioFund requireTracked(long ownerId, long legacyFundId);
    PortfolioFund requireTrackedByPortfolioFundId(long ownerId, long portfolioFundId);
    java.util.List<PortfolioFund> findTrackedByOwner(long ownerId);
    record PortfolioFund(long id, Long legacyFundId) {}
}
