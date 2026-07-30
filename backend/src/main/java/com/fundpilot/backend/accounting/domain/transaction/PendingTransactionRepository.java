package com.fundpilot.backend.accounting.domain.transaction;

public interface PendingTransactionRepository {
    boolean existsByPortfolioFund(long portfolioFundId, Long legacyFundId);
}
