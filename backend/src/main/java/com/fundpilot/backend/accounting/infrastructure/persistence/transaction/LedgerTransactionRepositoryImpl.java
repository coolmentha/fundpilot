package com.fundpilot.backend.accounting.infrastructure.persistence.transaction;

import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 账目流水仓储实现。
 *
 * <p>{@code ownerId} 与 legacy {@code fund_id} 都是 {@code portfolio_fund} 上的事实，
 * 属于扩展期的持久化细节，在这里解析，不污染领域模型。
 */
@Repository
@RequiredArgsConstructor
class LedgerTransactionRepositoryImpl implements TransactionRepository {

    private final LedgerTransactionJpaRepository transactions;
    private final JdbcTemplate jdbc;

    @Override
    public LedgerTransaction save(LedgerTransaction transaction) {
        LedgerTransactionJpaEntity entity;
        if (transaction.id() == null) {
            entity = LedgerTransactionPersistenceMapper.newEntity(
                    transaction, legacyFundId(transaction.portfolioFundId()));
        } else {
            entity = transactions.findById(transaction.id())
                    .orElseThrow(() -> new IllegalStateException("交易不存在: " + transaction.id()));
            LedgerTransactionPersistenceMapper.applyState(entity, transaction);
        }
        LedgerTransactionJpaEntity saved = transactions.save(entity);
        return LedgerTransactionPersistenceMapper.toDomain(saved, transaction.ownerId());
    }

    @Override
    public Optional<LedgerTransaction> findById(long transactionId) {
        return transactions.findById(transactionId).map(this::toDomain);
    }

    @Override
    public Optional<LedgerTransaction> findByIdForUpdate(long transactionId) {
        return transactions.findByIdForUpdate(transactionId).map(this::toDomain);
    }

    @Override
    public Optional<LedgerTransaction> findRelated(long transactionId) {
        return transactions.findById(transactionId)
                .map(LedgerTransactionJpaEntity::getRelatedTransactionId)
                .flatMap(this::findById);
    }

    @Override
    public List<LedgerTransaction> findByPortfolioFundAndStatus(long portfolioFundId,
                                                                TransactionStatus status) {
        return toDomain(transactions.findByPortfolioFundIdAndStatus(portfolioFundId, status.name()));
    }

    @Override
    public List<LedgerTransaction> findByPortfolioFundIdsAndStatus(Collection<Long> portfolioFundIds,
                                                                    TransactionStatus status) {
        if (portfolioFundIds.isEmpty()) {
            return List.of();
        }
        return toDomain(transactions.findByPortfolioFundIdInAndStatus(portfolioFundIds, status.name()));
    }

    @Override
    public List<LedgerTransaction> findByPortfolioFundOrderByTradeDateDesc(long portfolioFundId) {
        return toDomain(transactions.findByPortfolioFundOrderByTradeDateDesc(portfolioFundId));
    }

    @Override
    public List<LedgerTransaction> findByStatus(TransactionStatus status) {
        return toDomain(transactions.findByStatus(status.name()));
    }

    @Override
    public List<LedgerTransaction> findByStatusOrderByTradeDateDesc(TransactionStatus status) {
        return toDomain(transactions.findByStatusOrderByTradeDateDesc(status.name()));
    }

    @Override
    public boolean existsByPortfolioFundAndStatus(long portfolioFundId, TransactionStatus status) {
        return transactions.existsByPortfolioFundIdAndStatus(portfolioFundId, status.name());
    }

    @Override
    public List<HoldingShares> aggregateConfirmedShares(Collection<Long> portfolioFundIds) {
        if (portfolioFundIds.isEmpty()) {
            return List.of();
        }
        return transactions.aggregateConfirmedShares(portfolioFundIds).stream()
                .map(row -> new HoldingShares(row.getPortfolioFundId(), row.getHoldingShares()))
                .toList();
    }

    @Override
    public boolean existsByDcaPlanAndTradeDateBetween(long dcaPlanId, Instant startInclusive,
                                                      Instant endExclusive) {
        return transactions.existsByDcaPlanIdAndTradeDateBetween(dcaPlanId, startInclusive, endExclusive);
    }

    @Override
    public boolean existsByInvestmentPlanAndTradeDateBetween(long investmentPlanId, Instant startInclusive,
                                                              Instant endExclusive) {
        return transactions.existsByInvestmentPlanIdAndTradeDateBetween(
                investmentPlanId, startInclusive, endExclusive);
    }

    @Override
    public List<InvestmentPlanOccurrence> findInvestmentPlanOccurrences(long ownerId, Instant startInclusive,
                                                                          Instant endExclusive) {
        return transactions.findInvestmentPlanOccurrences(ownerId, startInclusive, endExclusive).stream()
                .map(row -> new InvestmentPlanOccurrence(row.getInvestmentPlanId(), row.getTradeDate(),
                        row.getAmount(), TransactionStatus.valueOf(row.getStatus())))
                .toList();
    }

    @Override
    public java.math.BigDecimal sumInvestedAmount(long ownerId, Instant startInclusive, Instant endExclusive) {
        return transactions.sumInvestedAmount(ownerId, startInclusive, endExclusive);
    }

    @Override
    public boolean existsByDisciplineAdviceId(long disciplineAdviceId) {
        return transactions.existsByDisciplineAdviceId(disciplineAdviceId);
    }

    private LedgerTransaction toDomain(LedgerTransactionJpaEntity entity) {
        return LedgerTransactionPersistenceMapper.toDomain(entity, ownerId(entity.getPortfolioFundId()));
    }

    private List<LedgerTransaction> toDomain(List<LedgerTransactionJpaEntity> entities) {
        Map<Long, Long> owners = ownersOf(entities.stream()
                .map(LedgerTransactionJpaEntity::getPortfolioFundId)
                .distinct()
                .toList());
        return entities.stream()
                .map(entity -> LedgerTransactionPersistenceMapper.toDomain(
                        entity, owners.getOrDefault(entity.getPortfolioFundId(), 0L)))
                .toList();
    }

    private long ownerId(Long portfolioFundId) {
        Long ownerId = jdbc.queryForObject(
                "SELECT owner_id FROM portfolio_fund WHERE id = ?", Long.class, portfolioFundId);
        if (ownerId == null) {
            throw new IllegalStateException("组合基金不存在: " + portfolioFundId);
        }
        return ownerId;
    }

    private Map<Long, Long> ownersOf(List<Long> portfolioFundIds) {
        Map<Long, Long> owners = new HashMap<>();
        if (portfolioFundIds.isEmpty()) {
            return owners;
        }
        String placeholders = String.join(",", portfolioFundIds.stream().map(id -> "?").toList());
        jdbc.query("SELECT id, owner_id FROM portfolio_fund WHERE id IN (" + placeholders + ")",
                rs -> {
                    owners.put(rs.getLong("id"), rs.getLong("owner_id"));
                },
                portfolioFundIds.toArray());
        return owners;
    }

    private long legacyFundId(long portfolioFundId) {
        Long legacyFundId = jdbc.queryForObject(
                "SELECT legacy_fund_id FROM portfolio_fund WHERE id = ?", Long.class, portfolioFundId);
        if (legacyFundId == null) {
            throw new IllegalStateException(
                    "组合基金 " + portfolioFundId + " 缺少 legacy fund 映射，扩展期不能写入账目");
        }
        return legacyFundId;
    }
}
