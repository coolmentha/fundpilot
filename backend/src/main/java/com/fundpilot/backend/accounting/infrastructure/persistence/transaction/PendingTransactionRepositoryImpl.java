package com.fundpilot.backend.accounting.infrastructure.persistence.transaction;

import com.fundpilot.backend.accounting.domain.transaction.PendingTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PendingTransactionRepositoryImpl implements PendingTransactionRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean existsByLegacyFundId(long legacyFundId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM fund_transaction
                    WHERE fund_id = ? AND status = 'PENDING' AND deleted_date IS NULL
                )
                """, Boolean.class, legacyFundId);
        return Boolean.TRUE.equals(exists);
    }
}
