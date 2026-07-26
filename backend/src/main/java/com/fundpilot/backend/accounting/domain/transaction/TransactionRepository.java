package com.fundpilot.backend.accounting.domain.transaction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 账目流水聚合的持久化需求。 */
public interface TransactionRepository {

    LedgerTransaction save(LedgerTransaction transaction);

    Optional<LedgerTransaction> findById(long transactionId);

    /** 悲观锁定单条流水，串行化并发编辑与确认。 */
    Optional<LedgerTransaction> findByIdForUpdate(long transactionId);

    Optional<LedgerTransaction> findRelated(long transactionId);

    List<LedgerTransaction> findByPortfolioFundAndStatus(long portfolioFundId, TransactionStatus status);

    List<LedgerTransaction> findByPortfolioFundOrderByTradeDateDesc(long portfolioFundId);

    List<LedgerTransaction> findByStatus(TransactionStatus status);

    /** 全局待处理流水，按业务交易时间倒序，供确认工作台使用。 */
    List<LedgerTransaction> findByStatusOrderByTradeDateDesc(TransactionStatus status);

    boolean existsByPortfolioFundAndStatus(long portfolioFundId, TransactionStatus status);

    /** 按组合基金聚合 CONFIRMED 净份额。 */
    List<HoldingShares> aggregateConfirmedShares(Collection<Long> portfolioFundIds);

    /** 定投幂等：某计划在时间区间内是否已生成任意状态流水。 */
    boolean existsByDcaPlanAndTradeDateBetween(long dcaPlanId, Instant startInclusive, Instant endExclusive);

    record HoldingShares(long portfolioFundId, java.math.BigDecimal holdingShares) {
    }
}
