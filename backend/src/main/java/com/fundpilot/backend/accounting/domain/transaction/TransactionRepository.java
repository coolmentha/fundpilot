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

    /** Insights 等查询组合批量读取已确认账目，避免按组合项逐条查询。 */
    List<LedgerTransaction> findByPortfolioFundIdsAndStatus(Collection<Long> portfolioFundIds,
                                                            TransactionStatus status);

    List<LedgerTransaction> findByPortfolioFundOrderByTradeDateDesc(long portfolioFundId);

    List<LedgerTransaction> findByStatus(TransactionStatus status);

    /** 全局待处理流水，按业务交易时间倒序，供确认工作台使用。 */
    List<LedgerTransaction> findByStatusOrderByTradeDateDesc(TransactionStatus status);

    boolean existsByPortfolioFundAndStatus(long portfolioFundId, TransactionStatus status);

    /** 按组合基金聚合 CONFIRMED 净份额。 */
    List<HoldingShares> aggregateConfirmedShares(Collection<Long> portfolioFundIds);

    /** 定投幂等：某计划在时间区间内是否已生成任意状态流水。 */
    boolean existsByDcaPlanAndTradeDateBetween(long dcaPlanId, Instant startInclusive, Instant endExclusive);

    /** 投资计划幂等：某计划在执行日只能生成一笔任意状态账目。 */
    boolean existsByInvestmentPlanAndTradeDateBetween(long investmentPlanId, Instant startInclusive,
                                                      Instant endExclusive);

    /** 计划预算预测所需的已生成业务日；CANCELLED 也保留幂等占位。 */
    List<InvestmentPlanOccurrence> findInvestmentPlanOccurrences(long ownerId, Instant startInclusive,
                                                                   Instant endExclusive);

    /** 月度预算：统计用户全部未取消的 INVEST，不限于自动计划来源。 */
    java.math.BigDecimal sumInvestedAmount(long ownerId, Instant startInclusive, Instant endExclusive);

    /** 建议回应幂等：同一 Discipline 建议至多生成一笔账目。 */
    boolean existsByDisciplineAdviceId(long disciplineAdviceId);

    record HoldingShares(long portfolioFundId, java.math.BigDecimal holdingShares) {
    }
    record InvestmentPlanOccurrence(long investmentPlanId, Instant tradeDate, java.math.BigDecimal amount,
                                    TransactionStatus status) {}
}
