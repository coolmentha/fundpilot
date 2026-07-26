package com.fundpilot.backend.accounting.domain.transaction;

public interface PendingTransactionRepository {
    boolean existsByLegacyFundId(long legacyFundId);
}
