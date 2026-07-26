package com.fundpilot.backend.accounting.application.gateway.transactionledger;

import java.util.Optional;

/**
 * 账目对“可记账组合基金”的出站契约，使用 Accounting 自己的语言。
 * <p>只暴露记账所需的事实：归属用户、对应产品与是否仍然有效；作废项立即被排除在计算之外。
 */
public interface TradedPortfolioFundGateway {

    Optional<TradedPortfolioFund> find(long portfolioFundId);

    Optional<TradedPortfolioFund> findOwned(long ownerId, long portfolioFundId);

    /** 扩展期按 legacy fund id 定位，供尚未迁移的入口复用。 */
    Optional<TradedPortfolioFund> findByLegacyFundId(long legacyFundId);

    java.util.List<TradedPortfolioFund> findTradableByOwner(long ownerId);

    /**
     * @param tradable 组合基金是否仍可记账；作废项为 false
     */
    record TradedPortfolioFund(long portfolioFundId, long ownerId, long fundProductId,
                               Long legacyFundId, boolean tradable) {
    }
}
